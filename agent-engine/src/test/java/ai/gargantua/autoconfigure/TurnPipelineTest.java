package ai.gargantua.autoconfigure;

import ai.gargantua.autoconfigure.guardrails.PiiInputGuardrail;
import ai.gargantua.autoconfigure.guardrails.RbacGuardrail;
import ai.gargantua.core.audit.AuditEvent;
import ai.gargantua.core.audit.AuditStore;
import ai.gargantua.core.exception.GuardrailBlockedException;
import ai.gargantua.core.exception.SkillNotFoundException;
import ai.gargantua.core.memory.ChatMessage;
import ai.gargantua.core.memory.WorkingMemoryPort;
import ai.gargantua.core.orchestrator.AgentRequest;
import ai.gargantua.core.orchestrator.RoutingResult;
import ai.gargantua.core.security.SecurityContext;
import ai.gargantua.core.session.DryRunContext;
import ai.gargantua.core.skill.SkillCard;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillRegistry;
import ai.gargantua.core.skill.SkillSource;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TurnPipeline} is the single place both chat endpoints get their pre- and
 * post-model steps from, so what is pinned here holds for sync and streaming alike.
 */
class TurnPipelineTest {

    private static final String RESTRICTED = "payroll-skill";
    private static final String OPEN = "faq-skill";

    private AgentProperties properties;
    private SemanticRoutingService routing;
    private SkillRegistry skillRegistry;
    private LlmProviderFactory llm;
    private ToolRegistry tools;
    private WorkingMemoryPort workingMemory;
    private AuditStore auditStore;

    @BeforeEach
    void setUp() {
        properties = new AgentProperties();
        routing = mock(SemanticRoutingService.class);
        skillRegistry = mock(SkillRegistry.class);
        llm = mock(LlmProviderFactory.class);
        tools = mock(ToolRegistry.class);
        workingMemory = mock(WorkingMemoryPort.class);
        auditStore = mock(AuditStore.class);

        when(skillRegistry.listMeta()).thenReturn(List.of());
        when(skillRegistry.load(RESTRICTED)).thenReturn(card(RESTRICTED, Set.of("hr")));
        when(skillRegistry.load(OPEN)).thenReturn(card(OPEN, Set.of()));
        when(skillRegistry.load("ghost")).thenThrow(new IllegalArgumentException("no such skill"));
        when(routing.route(anyString(), anyList())).thenReturn(RoutingResult.semantic(OPEN, 0.9));
        when(llm.resolveModelAlias(any(), any())).thenReturn("primary");
        when(llm.buildMessages(anyString(), anyString(), anyList())).thenCallRealMethod();
        when(tools.getFilteredTools(any())).thenReturn(List.of());
        when(tools.getToolSpecifications(any())).thenReturn(List.of());
    }

    private static SkillCard card(String name, Set<String> allowedRoles) {
        var meta = new SkillMeta(name, "", "1.0.0", true, false, "general",
                SkillSource.FILESYSTEM, allowedRoles);
        return new SkillCard(meta, "You are " + name, List.of(), null, List.of(), null, null, null, null);
    }

    private TurnPipeline pipeline() {
        var guardrails = new GuardrailPipeline(
                List.of(new RbacGuardrail(properties), new PiiInputGuardrail(properties)),
                List.of(), properties);
        var audit = new AuditService(auditStore, properties);
        return new TurnPipeline(guardrails, routing, new DefaultTokenBudgetManager(), llm,
                new PromptBuilder(), tools, properties, skillRegistry, List.of(), audit,
                null, workingMemory, null, null);
    }

    private static AgentRequest.Builder request(String message, String... roles) {
        return AgentRequest.builder()
                .message(message)
                .userId("alice")
                .sessionId("s1")
                .securityContext(new SecurityContext("alice", null, Set.of(roles)));
    }

    @Test
    void roleRestrictedSkillIsRefusedOnceRoutingHasResolvedIt() {
        // The first guardrail pass cannot know the skill; only the post-routing pass can
        // enforce allowed-roles. This is the check the streaming endpoint used to skip.
        var forced = request("show salaries").forceSkill(RESTRICTED).build();

        assertThatThrownBy(() -> pipeline().prepare(forced, null))
                .isInstanceOf(GuardrailBlockedException.class)
                .satisfies(e -> assertThat(((GuardrailBlockedException) e).getGuardrailName()).isEqualTo("rbac"));
    }

    @Test
    void roleRestrictedSkillIsServedToACallerHoldingTheRole() {
        var forced = request("show salaries", "hr").forceSkill(RESTRICTED).build();

        var turn = pipeline().prepare(forced, null);

        assertThat(turn.skillCard().meta().name()).isEqualTo(RESTRICTED);
        assertThat(turn.modelAlias()).isEqualTo("primary");
    }

    @Test
    void unknownSkillSurfacesAsSkillNotFound() {
        var forced = request("hi").forceSkill("ghost").build();

        assertThatThrownBy(() -> pipeline().prepare(forced, null))
                .isInstanceOf(SkillNotFoundException.class);
    }

    @Test
    void tenantIsPrefixedOntoTheSessionId() {
        var multiTenant = AgentRequest.builder().message("hi").userId("alice").sessionId("s1")
                .securityContext(new SecurityContext("alice", "acme", Set.of())).build();

        assertThat(pipeline().prepare(multiTenant, null).effectiveSessionId()).isEqualTo("acme:s1");
    }

    @Test
    void modelSeesTheMaskedMessageWhenPiiMaskingIsOn() {
        properties.getGuardrail().getInput().setPiiMaskingEnabled(true);

        var turn = pipeline().prepare(request("write to bob@example.com please").build(), null);

        assertThat(turn.llmUserMessage()).isEqualTo("write to [EMAIL_0] please");
        var last = (UserMessage) turn.messages().getLast();
        assertThat(last.singleText()).isEqualTo("write to [EMAIL_0] please");
        // The placeholder map stays available for the output guardrail to restore from.
        assertThat(turn.attributes()).containsKey("pii_map");
    }

    @Test
    void modelSeesTheOriginalMessageWhenPiiMaskingIsOff() {
        var turn = pipeline().prepare(request("write to bob@example.com please").build(), null);

        assertThat(turn.llmUserMessage()).isEqualTo("write to bob@example.com please");
    }

    @Test
    void listenerHearsEachStageInOrder() {
        var seen = new ArrayList<String>();

        pipeline().prepare(request("hi").build(), new TurnPipeline.Listener() {
            @Override public void onInputChecked(ai.gargantua.core.guardrail.GuardrailPipelineResult r) {
                seen.add("checked");
            }
            @Override public void onRouted(RoutingResult r) { seen.add("routed:" + r.skillName()); }
            @Override public void onSkillSelected(SkillCard c) { seen.add("skill:" + c.meta().name()); }
        });

        assertThat(seen).containsExactly("checked", "routed:" + OPEN, "skill:" + OPEN);
    }

    @Test
    void completeStoresTheExchangeInOneWriteAndAuditsIt() {
        properties.getAudit().setEnabled(true);
        var pipeline = pipeline();
        var req = request("hello").build();
        var turn = pipeline.prepare(req, null);

        var response = pipeline.complete(req, turn, "hi there", List.of("lookup"), System.currentTimeMillis());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> stored = ArgumentCaptor.forClass(List.class);
        verify(workingMemory, times(1)).appendMessages(eq("s1"), stored.capture());
        verify(workingMemory, never()).appendMessage(anyString(), any());
        assertThat(stored.getValue()).extracting(ChatMessage::role).containsExactly("user", "assistant");
        assertThat(stored.getValue()).extracting(ChatMessage::content).containsExactly("hello", "hi there");

        assertThat(response.text()).isEqualTo("hi there");
        assertThat(response.skillUsed()).isEqualTo(OPEN);
        assertThat(response.toolsCalled()).containsExactly("lookup");
        assertThat(response.dryRun()).isFalse();

        ArgumentCaptor<AuditEvent> audited = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditStore).record(audited.capture());
        assertThat(audited.getValue().userId()).isEqualTo("alice");
    }

    @Test
    void workingMemoryHoldsWhatTheModelSawNotTheRawPii() {
        properties.getGuardrail().getInput().setPiiMaskingEnabled(true);
        var pipeline = pipeline();
        var req = request("mail bob@example.com").build();
        var turn = pipeline.prepare(req, null);

        pipeline.complete(req, turn, "done", List.of(), System.currentTimeMillis());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> stored = ArgumentCaptor.forClass(List.class);
        verify(workingMemory).appendMessages(eq("s1"), stored.capture());
        assertThat(stored.getValue().getFirst().content()).isEqualTo("mail [EMAIL_0]");
    }

    @Test
    void auditTrailRecordsTheMaskedMessage() {
        properties.getGuardrail().getInput().setPiiMaskingEnabled(true);
        properties.getAudit().setEnabled(true);
        var pipeline = pipeline();
        var req = request("mail bob@example.com").build();

        pipeline.complete(req, pipeline.prepare(req, null), "done", List.of(), System.currentTimeMillis());

        ArgumentCaptor<AuditEvent> audited = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditStore).record(audited.capture());
        assertThat(audited.getValue().userMessage()).isEqualTo("mail [EMAIL_0]");
    }

    @Test
    void dryRunPersistsNothing() {
        var pipeline = pipeline();
        var req = request("hello").dryRunContext(DryRunContext.active(Map.of())).build();
        var turn = pipeline.prepare(req, null);

        var response = pipeline.complete(req, turn, "hi", List.of(), System.currentTimeMillis());

        verify(workingMemory, never()).appendMessages(anyString(), anyList());
        assertThat(response.dryRun()).isTrue();
    }
}
