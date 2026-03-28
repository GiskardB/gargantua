package ai.gargantua.adapters.web;

import ai.gargantua.core.memory.ChatMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * REST endpoint for exporting chat history in various formats (JSON, Markdown).
 * Useful for compliance, auditing, or user data portability.
 */
@RestController
@RequestMapping("/api/agent/chat/export")
@ConditionalOnBean(MongoTemplate.class)
@Tag(name = "History")
public class ChatExportController {

    private static final String MESSAGES_COLLECTION = "chat_messages";

    private final MongoTemplate mongoTemplate;

    public ChatExportController(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @GetMapping("/{userId}/{sessionId}")
    @Operation(summary = "Export session", description = "Exports a single session in the specified format.")
    @ApiResponse(responseCode = "200", description = "Exported session data")
    public ResponseEntity<String> exportSession(
            @PathVariable String userId,
            @PathVariable String sessionId,
            @Parameter(description = "Export format: json, txt, or md")
            @RequestParam(defaultValue = "json") String format) {

        Query query = new Query(
                Criteria.where("userId").is(userId).and("sessionId").is(sessionId))
                .with(Sort.by(Sort.Direction.ASC, "timestamp"));
        List<ChatMessage> messages = mongoTemplate.find(query, ChatMessage.class, MESSAGES_COLLECTION);

        return switch (format.toLowerCase()) {
            case "txt" -> ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"chat-" + sessionId + ".txt\"")
                    .body(formatAsText(messages));
            case "md" -> ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"chat-" + sessionId + ".md\"")
                    .body(formatAsMarkdown(messages, sessionId));
            default -> ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"chat-" + sessionId + ".json\"")
                    .body(formatAsJson(messages));
        };
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Export user data", description = "Exports all user messages within a date range.")
    @ApiResponse(responseCode = "200", description = "Exported user data")
    public ResponseEntity<String> exportUserData(
            @PathVariable String userId,
            @Parameter(description = "Start date (ISO-8601)") @RequestParam(required = false) String from,
            @Parameter(description = "End date (ISO-8601)") @RequestParam(required = false) String to,
            @Parameter(description = "Export format") @RequestParam(defaultValue = "json") String format) {

        Criteria criteria = Criteria.where("userId").is(userId);
        if (from != null) {
            criteria = criteria.and("timestamp").gte(Instant.parse(from));
        }
        if (to != null) {
            if (from == null) {
                criteria = criteria.and("timestamp").lte(Instant.parse(to));
            } else {
                criteria = criteria.andOperator(Criteria.where("timestamp").lte(Instant.parse(to)));
            }
        }

        Query query = new Query(criteria).with(Sort.by(Sort.Direction.ASC, "timestamp"));
        List<ChatMessage> messages = mongoTemplate.find(query, ChatMessage.class, MESSAGES_COLLECTION);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"export-" + userId + ".json\"")
                .body(formatAsJson(messages));
    }

    private String formatAsText(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : messages) {
            sb.append("[").append(msg.timestamp()).append("] ")
                    .append(msg.role().toUpperCase()).append(": ")
                    .append(msg.content()).append("\n\n");
        }
        return sb.toString();
    }

    private String formatAsMarkdown(List<ChatMessage> messages, String sessionId) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Chat Export - Session ").append(sessionId).append("\n\n");
        for (ChatMessage msg : messages) {
            String label = "user".equals(msg.role()) ? "**User**" : "**Assistant**";
            sb.append("### ").append(label).append(" (").append(msg.timestamp()).append(")\n\n");
            sb.append(msg.content()).append("\n\n---\n\n");
        }
        return sb.toString();
    }

    private String formatAsJson(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            sb.append("{\"role\":\"").append(msg.role())
                    .append("\",\"content\":\"").append(escapeJson(msg.content()))
                    .append("\",\"timestamp\":\"").append(msg.timestamp()).append("\"}");
            if (i < messages.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
