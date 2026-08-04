package ai.gargantua.autoconfigure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EnvironmentSecretResolver")
class EnvironmentSecretResolverTest {

    private static EnvironmentSecretResolver resolverOver(Map<String, String> environment) {
        return new EnvironmentSecretResolver(environment::get);
    }

    @Test
    @DisplayName("resolves a secret named exactly as declared")
    void resolvesExactName() {
        var resolver = resolverOver(Map.of("github-token", "ghp_direct"));

        assertThat(resolver.resolve("github-token")).contains("ghp_direct");
    }

    @Test
    @DisplayName("falls back to the conventional environment variable form")
    void fallsBackToUpperSnakeCase() {
        var resolver = resolverOver(Map.of("GITHUB_TOKEN", "ghp_env"));

        assertThat(resolver.resolve("github-token")).contains("ghp_env");
    }

    @Test
    @DisplayName("prefers the exact name over the normalised form")
    void exactNameWins() {
        var resolver = resolverOver(Map.of("github-token", "direct", "GITHUB_TOKEN", "normalised"));

        assertThat(resolver.resolve("github-token")).contains("direct");
    }

    @Test
    @DisplayName("normalises dots as well as hyphens")
    void normalisesDots() {
        var resolver = resolverOver(Map.of("PAYMENTS_API_KEY", "value"));

        assertThat(resolver.resolve("payments.api.key")).contains("value");
    }

    @Test
    @DisplayName("an unknown secret resolves to empty")
    void unknownSecretIsEmpty() {
        assertThat(resolverOver(Map.of()).resolve("missing")).isEmpty();
    }

    @Test
    @DisplayName("a blank value counts as absent")
    void blankValueIsAbsent() {
        assertThat(resolverOver(Map.of("token", "   ")).resolve("token")).isEmpty();
    }

    @Test
    @DisplayName("null and blank references resolve to empty")
    void nullOrBlankReferenceIsEmpty() {
        var resolver = resolverOver(Map.of("x", "y"));

        assertThat(resolver.resolve(null)).isEmpty();
        assertThat(resolver.resolve("  ")).isEmpty();
    }

    @Test
    @DisplayName("an already-normalised name is not looked up twice")
    void alreadyNormalisedNameLooksUpOnce() {
        assertThat(resolverOver(Map.of("OTHER", "value")).resolve("GITHUB_TOKEN")).isEmpty();
    }
}
