package ai.gargantua.memory.summarizer;

import ai.gargantua.core.memory.ChatMessage;
import ai.gargantua.core.memory.SessionSummary;
import ai.gargantua.core.session.SessionSummarizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Placeholder {@link SessionSummarizer} that concatenates message contents
 * into a summary. The real LLM-backed implementation will be wired in the
 * starter module.
 */
public class LlmSessionSummarizer implements SessionSummarizer {

    private static final Logger log = LoggerFactory.getLogger(LlmSessionSummarizer.class);

    @Override
    public SessionSummary summarize(String userId, String sessionId, List<ChatMessage> messages) {
        log.info("[SessionSummarizer] Summarizing {} messages for userId={}, sessionId={}",
                messages.size(), userId, sessionId);

        String summary = messages.stream()
                .map(m -> "%s: %s".formatted(m.role(), m.content()))
                .collect(Collectors.joining("\n"));

        // Extract simple key topics from unique user messages (first 5 words of each)
        List<String> keyTopics = messages.stream()
                .filter(m -> "user".equals(m.role()))
                .map(m -> {
                    String[] words = m.content().split("\\s+");
                    int limit = Math.min(words.length, 5);
                    return String.join(" ", java.util.Arrays.copyOf(words, limit));
                })
                .distinct()
                .limit(5)
                .toList();

        Instant sessionDate = messages.isEmpty()
                ? Instant.now()
                : messages.getFirst().timestamp();

        log.debug("[SessionSummarizer] Generated summary with {} key topics", keyTopics.size());

        return new SessionSummary(
                userId,
                sessionId,
                summary,
                keyTopics,
                List.of(),  // no unresolved items in placeholder
                messages.size(),
                sessionDate
        );
    }
}
