package io.agentkit.shell.client;

import io.agentkit.core.orchestrator.AgentRequest;
import io.agentkit.core.orchestrator.AgentResponse;
import io.agentkit.core.orchestrator.OrchestratorEngine;
import io.agentkit.core.session.DryRunContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Component
@ConditionalOnProperty(name = "agent.shell.mode", havingValue = "embedded", matchIfMissing = true)
public class EmbeddedAgentClient implements AgentClient {

    private final OrchestratorEngine orchestrator;

    public EmbeddedAgentClient(OrchestratorEngine orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public void stream(ShellChatRequest request, Consumer<SseEvent> eventConsumer) {
        try {
            DryRunContext dryRunContext = request.dryRun()
                    ? DryRunContext.active(Map.of())
                    : DryRunContext.inactive();

            AgentRequest agentRequest = AgentRequest.builder()
                    .message(request.message())
                    .sessionId(request.sessionId())
                    .userId(request.userId())
                    .forceSkill(request.forceSkill())
                    .dryRunContext(dryRunContext)
                    .build();

            AgentResponse response = orchestrator.invoke(agentRequest);

            // Emit tool call events
            if (response.toolsCalled() != null) {
                for (String tool : response.toolsCalled()) {
                    eventConsumer.accept(SseEvent.toolCall(tool));
                }
            }

            // Emit token events (split response text for streaming simulation)
            if (response.text() != null && !response.text().isEmpty()) {
                eventConsumer.accept(SseEvent.token(response.text()));
            }

            // Emit meta event
            eventConsumer.accept(SseEvent.meta(
                    response.skillUsed(),
                    response.routingMethod() != null ? response.routingMethod().name() : null,
                    response.routingConfidence(),
                    response.inputTokens(),
                    response.outputTokens()
            ));

            // Emit done event
            eventConsumer.accept(SseEvent.done());

        } catch (Exception e) {
            eventConsumer.accept(SseEvent.error(e.getMessage()));
        }
    }

    @Override
    public void resolveApproval(String requestId, String decision) {
        // In embedded mode, approval is handled inline via the orchestrator
        // This is a no-op as the approval flow is synchronous in embedded mode
    }

    @Override
    public String newSession() {
        return UUID.randomUUID().toString();
    }
}
