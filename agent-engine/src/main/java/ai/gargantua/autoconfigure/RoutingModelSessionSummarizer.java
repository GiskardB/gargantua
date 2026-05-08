package ai.gargantua.autoconfigure;

import ai.gargantua.core.memory.ChatMessage;
import ai.gargantua.core.memory.SessionSummary;
import ai.gargantua.core.session.SessionSummarizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Real LLM-backed {@link SessionSummarizer} that calls the routing model
 * (typically a small/local model like Ollama, configured at
 * {@code agent.llm.routing-model.*}) to compress an expired session into an
 * episodic-memory record. Falls back to a deterministic concatenation when the
 * routing model is unavailable or returns malformed output, so the caller
 * always gets a non-null {@link SessionSummary}.
 *
 * <p>The prompt asks the model for a single JSON object with three fields —
 * {@code summary}, {@code keyTopics} (≤ 5), {@code unresolvedItems} (≤ 5).
 * Anything else is ignored.</p>
 */
public class RoutingModelSessionSummarizer implements SessionSummarizer {

    private static final Logger log = LoggerFactory.getLogger(RoutingModelSessionSummarizer.class);

    private static final String SYSTEM_PROMPT = """
            You are a session-summarisation assistant. Given a transcript of a chat session,
            return a single JSON object with exactly these fields:
              - "summary": one paragraph (≤ 800 chars) describing what was discussed and decided.
              - "keyTopics": up to 5 short topic labels (1–4 words each).
              - "unresolvedItems": up to 5 outstanding questions or pending tasks raised by the user
                                    that were not answered. Empty array when everything was resolved.
            Respond with the JSON object only — no prose, no code fences.
            """;

    private final LlmProviderFactory llmProviderFactory;
    private final ObjectMapper objectMapper;

    public RoutingModelSessionSummarizer(LlmProviderFactory llmProviderFactory) {
        this.llmProviderFactory = llmProviderFactory;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public SessionSummary summarize(String userId, String sessionId, List<ChatMessage> messages) {
        log.info("[SessionSummarizer] Summarising {} messages for userId={}, sessionId={}",
                messages.size(), userId, sessionId);

        Instant sessionDate = messages.isEmpty()
                ? Instant.now()
                : messages.getFirst().timestamp();

        if (messages.isEmpty()) {
            return new SessionSummary(userId, sessionId, "", List.of(), List.of(),
                    0, sessionDate);
        }

        StringBuilder transcript = new StringBuilder();
        for (ChatMessage m : messages) {
            transcript.append(m.role()).append(": ").append(m.content()).append('\n');
        }

        try {
            ChatModel routingModel = llmProviderFactory.getRoutingModel();
            String response = routingModel.chat(
                    SystemMessage.from(SYSTEM_PROMPT),
                    UserMessage.from(transcript.toString())
            ).aiMessage().text();
            return parseSummary(userId, sessionId, response, messages.size(), sessionDate);
        } catch (Exception e) {
            log.warn("[SessionSummarizer] Routing model failed ({}), falling back to placeholder", e.getMessage());
            return placeholder(userId, sessionId, messages, sessionDate);
        }
    }

    private SessionSummary parseSummary(String userId, String sessionId, String raw,
                                         int messageCount, Instant sessionDate) {
        if (raw == null || raw.isBlank()) {
            return placeholder(userId, sessionId, List.of(), sessionDate);
        }
        String json = stripCodeFences(raw).trim();
        try {
            JsonNode node = objectMapper.readTree(json);
            String summary = textField(node, "summary");
            List<String> topics = stringList(node.get("keyTopics"), 5);
            List<String> unresolved = stringList(node.get("unresolvedItems"), 5);
            return new SessionSummary(userId, sessionId, summary,
                    topics, unresolved, messageCount, sessionDate);
        } catch (Exception e) {
            log.warn("[SessionSummarizer] Could not parse JSON summary ({}). Raw output: {}",
                    e.getMessage(), raw.length() > 200 ? raw.substring(0, 200) + "…" : raw);
            // Treat the whole text as the summary when JSON parsing fails.
            return new SessionSummary(userId, sessionId, raw.strip(),
                    List.of(), List.of(), messageCount, sessionDate);
        }
    }

    private SessionSummary placeholder(String userId, String sessionId,
                                        List<ChatMessage> messages, Instant sessionDate) {
        StringBuilder concat = new StringBuilder();
        for (ChatMessage m : messages) {
            concat.append(m.role()).append(": ").append(m.content()).append('\n');
        }
        return new SessionSummary(userId, sessionId, concat.toString(),
                List.of(), List.of(), messages.size(), sessionDate);
    }

    private static String stripCodeFences(String raw) {
        String s = raw.strip();
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            int closingFence = s.lastIndexOf("```");
            if (firstNewline > 0 && closingFence > firstNewline) {
                return s.substring(firstNewline + 1, closingFence);
            }
        }
        return s;
    }

    private static String textField(JsonNode node, String name) {
        JsonNode v = node != null ? node.get(name) : null;
        return v != null && !v.isNull() ? v.asText("") : "";
    }

    private static List<String> stringList(JsonNode node, int cap) {
        if (node == null || !node.isArray() || node.isEmpty()) return List.of();
        List<String> out = new ArrayList<>();
        for (JsonNode element : node) {
            if (element == null || element.isNull()) continue;
            String s = element.asText("");
            if (s != null && !s.isBlank()) {
                out.add(s);
                if (out.size() >= cap) break;
            }
        }
        return Collections.unmodifiableList(out);
    }
}
