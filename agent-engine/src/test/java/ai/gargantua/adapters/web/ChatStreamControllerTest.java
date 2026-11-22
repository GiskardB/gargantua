package ai.gargantua.adapters.web;

import ai.gargantua.autoconfigure.AgentProperties;
import ai.gargantua.autoconfigure.DefaultTokenBudgetManager;
import ai.gargantua.autoconfigure.GuardrailPipeline;
import ai.gargantua.autoconfigure.LlmProviderFactory;
import ai.gargantua.autoconfigure.PromptBuilder;
import ai.gargantua.autoconfigure.SecurityContextFilter;
import ai.gargantua.autoconfigure.SemanticRoutingService;
import ai.gargantua.autoconfigure.ToolRegistry;
import ai.gargantua.autoconfigure.TurnPipeline;
import ai.gargantua.autoconfigure.guardrails.RbacGuardrail;
import ai.gargantua.core.memory.ChatMessage;
import ai.gargantua.core.memory.WorkingMemoryPort;
import ai.gargantua.core.security.SecurityContext;
import ai.gargantua.core.skill.SkillCard;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillRegistry;
import ai.gargantua.core.skill.SkillSource;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatStreamControllerTest {

    private static final String RESTRICTED = "payroll-skill";

    private final AtomicInteger modelCalls = new AtomicInteger();
    private WorkingMemoryPort workingMemory;
    private ChatStreamController controller;

    /** Streams two tokens, then completes — enough to drive the controller end to end. */
    private final StreamingChatModel model = new StreamingChatModel() {
        @Override
        public void chat(ChatRequest request, StreamingChatResponseHandler handler) {
            modelCalls.incrementAndGet();
            handler.onPartialResponse("Hel");
            handler.onPartialResponse("lo");
            handler.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.from("Hello"))
                    .tokenUsage(new dev.langchain4j.model.output.TokenUsage(50, 7))
                    .build());
        }
    };

    @BeforeEach
    void setUp() {
        var properties = new AgentProperties();
        var skillRegistry = mock(SkillRegistry.class);
        var llm = mock(LlmProviderFactory.class);
        var tools = mock(ToolRegistry.class);
        workingMemory = mock(WorkingMemoryPort.class);

        var meta = new SkillMeta(RESTRICTED, "", "1.0.0", true, false, "general",
                SkillSource.FILESYSTEM, Set.of("hr"));
        when(skillRegistry.listMeta()).thenReturn(List.of(meta));
        when(skillRegistry.load(RESTRICTED)).thenReturn(
                new SkillCard(meta, "You run payroll", List.of(), null, List.of(), null, null, null, null));
        when(llm.resolveModelAlias(any(), any())).thenReturn("primary");
        when(llm.buildMessages(anyString(), anyString(), anyList())).thenCallRealMethod();
        when(llm.getStreamingModel("primary")).thenReturn(model);
        when(tools.getFilteredTools(any())).thenReturn(List.of());
        when(tools.getToolSpecifications(any())).thenReturn(List.of());

        var guardrails = new GuardrailPipeline(List.of(new RbacGuardrail(properties)), List.of(), properties);
        var pipeline = new TurnPipeline(guardrails, mock(SemanticRoutingService.class),
                new DefaultTokenBudgetManager(), llm, new PromptBuilder(), tools, properties,
                skillRegistry, List.of(), null, null, workingMemory, null, null);
        controller = new ChatStreamController(pipeline, llm, guardrails, tools, properties, null);
    }

    private List<ServerSentEvent<String>> stream(String... roles) {
        var http = new MockHttpServletRequest();
        http.setAttribute(SecurityContextFilter.SECURITY_CONTEXT_ATTR,
                new SecurityContext("alice", null, Set.of(roles)));
        return controller.streamChat(new ChatStreamController.ChatRequest("show salaries"),
                        "alice", "s1", false, RESTRICTED, http)
                .collectList().block(Duration.ofSeconds(10));
    }

    @Test
    void roleRestrictedSkillIsRefusedBeforeTheModelIsCalled() {
        var events = stream();

        assertThat(events).extracting(ServerSentEvent::event).containsExactly("error");
        assertThat(events.getFirst().data()).contains("GUARDRAIL_BLOCKED").contains("Blocked by guardrail");
        assertThat(modelCalls).hasValue(0);
        verify(workingMemory, never()).appendMessages(anyString(), anyList());
    }

    @Test
    void callerHoldingTheRoleGetsTokensThenDoneAndTheTurnIsStored() {
        var events = stream("hr");

        assertThat(events).extracting(ServerSentEvent::event).containsExactly("token", "token", "done");
        assertThat(events.getLast().data())
                .contains("\"skillUsed\":\"" + RESTRICTED + "\"")
                .contains("\"sessionId\":\"s1\"")
                // The provider's own counts, not a length-based guess.
                .contains("\"totalTokens\":57");
        assertThat(modelCalls).hasValue(1);
        verify(workingMemory).appendMessages(eq("s1"), org.mockito.ArgumentMatchers.<List<ChatMessage>>argThat(
                messages -> messages.size() == 2 && "Hello".equals(messages.get(1).content())));
    }
}
