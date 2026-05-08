package ai.gargantua.autoconfigure;

import ai.gargantua.core.memory.ChatMessage;
import ai.gargantua.core.memory.EpisodicMemoryPort;
import ai.gargantua.core.memory.SessionSummary;
import ai.gargantua.core.session.SessionSummarizer;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Scheduled job that finds sessions whose working-memory TTL has elapsed and
 * compresses them into episodic-memory summaries via {@link SessionSummarizer}.
 *
 * <p>The trigger is intentionally Mongo-based rather than Redis-keyspace based
 * because once a working-memory key expires in Redis the messages are gone —
 * we'd have nothing to summarise. {@code DefaultOrchestratorEngine} persists
 * every chat message into the {@code chat_messages} Mongo collection and an
 * aggregate {@code chat_sessions} document with {@code lastMessageAt}; this job
 * scans that collection.</p>
 *
 * <p>To avoid double-summarising, sessions are marked {@code summarized: true}
 * after a successful summarisation; the query filters on
 * {@code summarized: {$ne: true}}.</p>
 */
public class SessionExpirySummarizationScheduler {

    private static final Logger log = LoggerFactory.getLogger(SessionExpirySummarizationScheduler.class);

    private static final String CHAT_SESSIONS = "chat_sessions";
    private static final String CHAT_MESSAGES = "chat_messages";

    private final MongoTemplate mongo;
    private final AgentProperties properties;
    private final SessionSummarizer summarizer;

    @Nullable
    private final EpisodicMemoryPort episodicMemoryPort;

    public SessionExpirySummarizationScheduler(MongoTemplate mongo,
                                               AgentProperties properties,
                                               SessionSummarizer summarizer,
                                               @Nullable EpisodicMemoryPort episodicMemoryPort) {
        this.mongo = mongo;
        this.properties = properties;
        this.summarizer = summarizer;
        this.episodicMemoryPort = episodicMemoryPort;
    }

    @Scheduled(fixedDelayString = "#{${agent.summarization.scan-interval-minutes:5} * 60 * 1000}",
            initialDelayString = "#{${agent.summarization.scan-interval-minutes:5} * 60 * 1000}")
    public void scanAndSummarize() {
        if (!properties.getSummarization().isEnabled()) {
            return;
        }
        int ttl = properties.getMemory().getWorking().getTtlMinutes();
        int grace = properties.getSummarization().getGraceMinutes();
        Instant cutoff = Instant.now().minusSeconds(60L * (ttl + Math.max(0, grace)));

        Query q = new Query(Criteria.where("lastMessageAt").lt(cutoff)
                .and("summarized").ne(true));
        List<Document> sessions = mongo.find(q, Document.class, CHAT_SESSIONS);
        if (sessions.isEmpty()) {
            log.debug("[Summarization] No expired sessions older than {} (ttl={}m, grace={}m)",
                    cutoff, ttl, grace);
            return;
        }
        log.info("[Summarization] Found {} expired sessions to summarise", sessions.size());
        for (Document session : sessions) {
            String userId = session.getString("userId");
            String sessionId = session.getString("sessionId");
            if (userId == null || sessionId == null) continue;
            try {
                summarizeSession(userId, sessionId);
            } catch (Exception e) {
                log.warn("[Summarization] Failed to summarise session userId={}, sessionId={}: {}",
                        userId, sessionId, e.getMessage());
            }
        }
    }

    void summarizeSession(String userId, String sessionId) {
        List<ChatMessage> messages = loadMessages(userId, sessionId);
        if (messages.isEmpty()) {
            markSummarized(userId, sessionId);
            return;
        }
        SessionSummary summary = summarizer.summarize(userId, sessionId, messages);
        if (episodicMemoryPort != null) {
            episodicMemoryPort.saveSummary(summary);
        } else {
            mongo.insert(summary, "session_summaries");
        }
        markSummarized(userId, sessionId);
        log.info("[Summarization] Summarised userId={}, sessionId={} ({} messages)",
                userId, sessionId, messages.size());
    }

    private List<ChatMessage> loadMessages(String userId, String sessionId) {
        Query q = new Query(Criteria.where("userId").is(userId)
                .and("sessionId").is(sessionId)).limit(2000);
        List<Document> docs = mongo.find(q, Document.class, CHAT_MESSAGES);
        List<ChatMessage> out = new ArrayList<>(docs.size());
        for (Document d : docs) {
            String role = d.getString("role");
            String content = d.getString("content");
            Object ts = d.get("timestamp");
            Instant timestamp = ts instanceof java.util.Date date ? date.toInstant() : Instant.now();
            if (role != null && content != null) {
                out.add(new ChatMessage(role, content, timestamp));
            }
        }
        return out;
    }

    private void markSummarized(String userId, String sessionId) {
        Query q = new Query(Criteria.where("userId").is(userId).and("sessionId").is(sessionId));
        mongo.upsert(q, new Update().set("summarized", true)
                        .set("summarizedAt", Instant.now()),
                CHAT_SESSIONS);
    }
}
