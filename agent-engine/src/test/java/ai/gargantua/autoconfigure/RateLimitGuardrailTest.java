package ai.gargantua.autoconfigure;

import ai.gargantua.autoconfigure.guardrails.RateLimitGuardrail;
import ai.gargantua.autoconfigure.guardrails.RbacGuardrail;
import ai.gargantua.core.guardrail.GuardrailInputContext;
import ai.gargantua.core.guardrail.GuardrailResult;
import ai.gargantua.core.guardrail.GuardrailVerdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RateLimitGuardrail")
class RateLimitGuardrailTest {

    private AgentProperties propsWithRateLimit(boolean enabled) {
        AgentProperties props = new AgentProperties();
        props.getGuardrail().getInput().setRateLimitEnabled(enabled);
        return props;
    }

    private GuardrailInputContext ctx(String message) {
        return new GuardrailInputContext(message, "user1", "session1", null, new HashMap<>());
    }

    // --- name() ---

    @Test
    @DisplayName("name() returns 'rate-limit'")
    void name_returnsRateLimit() {
        var guardrail = new RateLimitGuardrail(new AgentProperties());
        assertThat(guardrail.name()).isEqualTo("rate-limit");
    }

    // --- isEnabled() ---

    @Test
    @DisplayName("isEnabled() returns true when rate limit is enabled")
    void isEnabled_trueWhenEnabled() {
        AgentProperties props = propsWithRateLimit(true);
        var guardrail = new RateLimitGuardrail(props);
        assertThat(guardrail.isEnabled(props)).isTrue();
    }

    @Test
    @DisplayName("isEnabled() returns false when rate limit is disabled")
    void isEnabled_falseWhenDisabled() {
        AgentProperties props = propsWithRateLimit(false);
        var guardrail = new RateLimitGuardrail(props);
        assertThat(guardrail.isEnabled(props)).isFalse();
    }

    @Test
    @DisplayName("isEnabled() falls back to injected props when argument is not AgentProperties")
    void isEnabled_fallsBackToInjectedProps() {
        AgentProperties props = propsWithRateLimit(true);
        var guardrail = new RateLimitGuardrail(props);
        assertThat(guardrail.isEnabled("something-else")).isTrue();
    }

    @Test
    @DisplayName("isEnabled() returns false by default")
    void isEnabled_falseByDefault() {
        var guardrail = new RateLimitGuardrail(new AgentProperties());
        assertThat(guardrail.isEnabled(new AgentProperties())).isFalse();
    }

    // --- check() ---

    @Test
    @DisplayName("check() always returns PASS (placeholder implementation)")
    void check_alwaysPasses() {
        var guardrail = new RateLimitGuardrail(propsWithRateLimit(true));

        GuardrailResult result = guardrail.check(ctx("Hello"));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
        assertThat(result.guardrailName()).isEqualTo("rate-limit");
    }

    @Test
    @DisplayName("check() passes with null message")
    void check_passesWithNullMessage() {
        var guardrail = new RateLimitGuardrail(propsWithRateLimit(true));

        GuardrailResult result = guardrail.check(ctx(null));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
    }

    @Test
    @DisplayName("check() passes with empty message")
    void check_passesWithEmptyMessage() {
        var guardrail = new RateLimitGuardrail(propsWithRateLimit(true));

        GuardrailResult result = guardrail.check(ctx(""));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
    }

    // --- enforcement ---

    private static AgentProperties limited(int max, int windowSeconds) {
        AgentProperties props = new AgentProperties();
        props.getGuardrail().getInput().setRateLimitEnabled(true);
        props.getGuardrail().getInput().setRateLimitMaxRequests(max);
        props.getGuardrail().getInput().setRateLimitWindowSeconds(windowSeconds);
        return props;
    }

    private static GuardrailInputContext from(String userId, String tenantId) {
        var attributes = new HashMap<String, Object>();
        attributes.put(RbacGuardrail.SECURITY_CONTEXT_KEY,
                new ai.gargantua.core.security.SecurityContext(userId, tenantId, java.util.Set.of()));
        return new GuardrailInputContext("hi", userId, "s", null, attributes);
    }

    @Test
    @DisplayName("blocks the request that exceeds the limit, and admits again once the window has slid")
    void blocksOverTheLimitThenRecovers() {
        var clock = new java.util.concurrent.atomic.AtomicLong(1_000_000);
        var guardrail = new RateLimitGuardrail(limited(2, 10),
                new ai.gargantua.adapters.ratelimit.InMemoryRequestRateLimiter(clock::get));

        assertThat(guardrail.check(ctx("a")).verdict()).isEqualTo(GuardrailVerdict.PASS);
        assertThat(guardrail.check(ctx("b")).verdict()).isEqualTo(GuardrailVerdict.PASS);
        var third = guardrail.check(ctx("c"));
        assertThat(third.verdict()).isEqualTo(GuardrailVerdict.BLOCK);
        assertThat(third.reason()).contains("2 requests per 10 seconds");

        clock.addAndGet(10_001);
        assertThat(guardrail.check(ctx("d")).verdict()).isEqualTo(GuardrailVerdict.PASS);
    }

    @Test
    @DisplayName("a blocked request is not counted, so a caller hammering the limit is not locked out forever")
    void blockedRequestsDoNotExtendTheWindow() {
        var clock = new java.util.concurrent.atomic.AtomicLong(1_000_000);
        var guardrail = new RateLimitGuardrail(limited(1, 10),
                new ai.gargantua.adapters.ratelimit.InMemoryRequestRateLimiter(clock::get));

        guardrail.check(ctx("a"));
        clock.addAndGet(9_000);
        assertThat(guardrail.check(ctx("b")).verdict()).isEqualTo(GuardrailVerdict.BLOCK);
        clock.addAndGet(1_500);
        assertThat(guardrail.check(ctx("c")).verdict()).isEqualTo(GuardrailVerdict.PASS);
    }

    @Test
    @DisplayName("limits are per caller, and the same user id in two tenants is two callers")
    void limitsArePerCallerAndPerTenant() {
        var guardrail = new RateLimitGuardrail(limited(1, 60));

        assertThat(guardrail.check(from("alice", "acme")).verdict()).isEqualTo(GuardrailVerdict.PASS);
        assertThat(guardrail.check(from("alice", "acme")).verdict()).isEqualTo(GuardrailVerdict.BLOCK);
        assertThat(guardrail.check(from("alice", "globex")).verdict()).isEqualTo(GuardrailVerdict.PASS);
        assertThat(guardrail.check(from("bob", "acme")).verdict()).isEqualTo(GuardrailVerdict.PASS);
    }

    @Test
    @DisplayName("the post-routing pass does not count the request a second time")
    void postRoutingPassIsNotCounted() {
        var guardrail = new RateLimitGuardrail(limited(1, 60));
        var skill = new ai.gargantua.core.skill.SkillMeta("s", "", "1", true, false, "general",
                ai.gargantua.core.skill.SkillSource.FILESYSTEM, java.util.Set.of());
        var postRouting = new GuardrailInputContext("hi", "user1", "session1", skill, new HashMap<>());

        assertThat(guardrail.check(ctx("hi")).verdict()).isEqualTo(GuardrailVerdict.PASS);
        // Same request, second pass: over the limit if it were counted again.
        assertThat(guardrail.check(postRouting).verdict()).isEqualTo(GuardrailVerdict.PASS);
    }
}
