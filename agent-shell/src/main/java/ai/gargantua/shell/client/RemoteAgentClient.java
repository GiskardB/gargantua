package ai.gargantua.shell.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Shell client that communicates with a remote agent server via HTTP REST calls.
 * Activated when {@code agent.shell.mode=remote}. Connects to the URL configured
 * via {@code agent.shell.remote.url}.
 */
@Component
@ConditionalOnProperty(name = "agent.shell.mode", havingValue = "remote")
public class RemoteAgentClient implements AgentClient {

    private final RestClient restClient;

    public RemoteAgentClient(
            @Value("${agent.shell.remote.url:http://localhost:8080}") String baseUrl,
            @Value("${agent.shell.remote.api-key:}") String apiKey,
            @Value("${agent.shell.remote.timeout-ms:30000}") int timeoutMs) {

        var builder = RestClient.builder()
                .baseUrl(baseUrl);

        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + apiKey);
        }

        this.restClient = builder.build();
    }

    @Override
    public void stream(ShellChatRequest request, Consumer<SseEvent> eventConsumer) {
        try {
            var body = Map.of(
                    "message", request.message(),
                    "sessionId", request.sessionId() != null ? request.sessionId() : "",
                    "userId", request.userId() != null ? request.userId() : "",
                    "dryRun", request.dryRun(),
                    "forceSkill", request.forceSkill() != null ? request.forceSkill() : ""
            );

            // Simplified: sends POST and treats response as a single text block.
            // A production implementation would use WebClient for real SSE streaming.
            var responseText = restClient.post()
                    .uri("/api/agent/chat")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            if (responseText != null && !responseText.isEmpty()) {
                eventConsumer.accept(SseEvent.token(responseText));
            }

            eventConsumer.accept(SseEvent.done());

        } catch (Exception e) {
            eventConsumer.accept(SseEvent.error(e.getMessage()));
        }
    }

    @Override
    public void resolveApproval(String requestId, String decision) {
        try {
            var body = Map.of(
                    "requestId", requestId,
                    "decision", decision
            );

            restClient.post()
                    .uri("/api/agent/approval")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            // Silently handle approval resolution failures in remote mode
        }
    }

    @Override
    public String newSession() {
        try {
            String sessionId = restClient.post()
                    .uri("/api/agent/session")
                    .retrieve()
                    .body(String.class);
            return sessionId != null ? sessionId : UUID.randomUUID().toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }
}
