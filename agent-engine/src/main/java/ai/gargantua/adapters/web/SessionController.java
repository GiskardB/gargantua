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
@Tag(
        name = "Chat — Sessions",
        description = "Helper endpoint for client UIs that need a fresh session id without making a "
                + "chat request first. The id format is `sess_<uuid>`; pass it back as "
                + "`X-Session-Id` on subsequent chat calls so working memory stays warm across turns."
)
public class SessionController {

    @PostMapping("/new")
    @Operation(
            summary = "Mint a new session id",
            description = "Generates a new unique `sessionId` (`sess_<uuid>`). Stateless — no record "
                    + "is created on the server until the first chat message under this id arrives."
    )
    @ApiResponse(responseCode = "200",
            description = "Object with one field: `{\"sessionId\": \"sess_<uuid>\"}`.")
    public ResponseEntity<Map<String, String>> newSession() {
        String sessionId = "sess_" + UUID.randomUUID();
        return ResponseEntity.ok(Map.of("sessionId", sessionId));
    }
}
