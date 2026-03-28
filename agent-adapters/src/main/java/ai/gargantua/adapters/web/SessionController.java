package ai.gargantua.adapters.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * REST endpoint for session lifecycle management. Creates new unique session
 * identifiers for chat interactions.
 */
@RestController
@RequestMapping("/api/agent/session")
@Tag(name = "Chat")
public class SessionController {

    @PostMapping("/new")
    @Operation(
            summary = "Create a new session",
            description = "Generates a new unique session identifier for chat interactions."
    )
    @ApiResponse(responseCode = "200", description = "New session created")
    public ResponseEntity<Map<String, String>> newSession() {
        String sessionId = "sess_" + UUID.randomUUID();
        return ResponseEntity.ok(Map.of("sessionId", sessionId));
    }
}
