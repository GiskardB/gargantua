package io.agentkit.adapters.web;

import io.agentkit.core.memory.ChatMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agent/chat")
@Tag(name = "History")
public class ChatHistoryController {

    private static final String SESSIONS_COLLECTION = "chat_sessions";
    private static final String MESSAGES_COLLECTION = "chat_messages";

    private final MongoTemplate mongoTemplate;

    public ChatHistoryController(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @GetMapping("/sessions/{userId}")
    @Operation(summary = "List user sessions", description = "Returns paginated list of sessions for a user.")
    @ApiResponse(responseCode = "200", description = "List of sessions")
    public ResponseEntity<List<Map>> listSessions(
            @PathVariable String userId,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        Query query = new Query(Criteria.where("userId").is(userId))
                .with(Sort.by(Sort.Direction.DESC, "lastMessageAt"))
                .skip((long) page * size)
                .limit(size);
        List<Map> sessions = mongoTemplate.find(query, Map.class, SESSIONS_COLLECTION);
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/history/{userId}/{sessionId}")
    @Operation(summary = "Get chat history", description = "Returns paginated messages for a specific session.")
    @ApiResponse(responseCode = "200", description = "List of messages")
    public ResponseEntity<List<ChatMessage>> getHistory(
            @PathVariable String userId,
            @PathVariable String sessionId,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "50") int size) {

        Query query = new Query(
                Criteria.where("userId").is(userId).and("sessionId").is(sessionId))
                .with(Sort.by(Sort.Direction.ASC, "timestamp"))
                .skip((long) page * size)
                .limit(size);
        List<ChatMessage> messages = mongoTemplate.find(query, ChatMessage.class, MESSAGES_COLLECTION);
        return ResponseEntity.ok(messages);
    }

    @GetMapping("/history/{userId}/search")
    @Operation(summary = "Search chat history", description = "Full-text search across user's chat history.")
    @ApiResponse(responseCode = "200", description = "Search results")
    public ResponseEntity<List<ChatMessage>> searchHistory(
            @PathVariable String userId,
            @RequestParam("q") String queryText,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        TextCriteria textCriteria = TextCriteria.forDefaultLanguage().matching(queryText);
        Query query = TextQuery.queryText(textCriteria)
                .addCriteria(Criteria.where("userId").is(userId))
                .with(Sort.by(Sort.Direction.DESC, "timestamp"))
                .skip((long) page * size)
                .limit(size);
        List<ChatMessage> results = mongoTemplate.find(query, ChatMessage.class, MESSAGES_COLLECTION);
        return ResponseEntity.ok(results);
    }

    @DeleteMapping("/history/{userId}/{sessionId}")
    @Operation(summary = "Delete session", description = "Deletes a specific session and all its messages.")
    @ApiResponse(responseCode = "200", description = "Session deleted")
    public ResponseEntity<Map<String, String>> deleteSession(
            @PathVariable String userId,
            @PathVariable String sessionId) {

        Query sessionQuery = new Query(
                Criteria.where("userId").is(userId).and("sessionId").is(sessionId));
        mongoTemplate.remove(sessionQuery, SESSIONS_COLLECTION);

        Query messagesQuery = new Query(
                Criteria.where("userId").is(userId).and("sessionId").is(sessionId));
        mongoTemplate.remove(messagesQuery, MESSAGES_COLLECTION);

        return ResponseEntity.ok(Map.of("status", "deleted", "sessionId", sessionId));
    }

    @DeleteMapping("/history/{userId}")
    @Operation(summary = "Delete all user data (GDPR)", description = "Deletes all chat sessions and messages for a user.")
    @ApiResponse(responseCode = "200", description = "All user data deleted")
    public ResponseEntity<Map<String, String>> deleteAllUserData(@PathVariable String userId) {

        Query query = new Query(Criteria.where("userId").is(userId));
        mongoTemplate.remove(query, SESSIONS_COLLECTION);
        mongoTemplate.remove(query, MESSAGES_COLLECTION);

        return ResponseEntity.ok(Map.of("status", "deleted", "userId", userId));
    }
}
