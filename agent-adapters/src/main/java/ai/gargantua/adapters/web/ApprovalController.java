package ai.gargantua.adapters.web;

import ai.gargantua.core.exception.ApprovalExpiredException;
import ai.gargantua.core.hitl.ApprovalDecision;
import ai.gargantua.core.hitl.ApprovalRequest;
import ai.gargantua.core.hitl.ApprovalStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/agent/approval")
@Tag(name = "Chat")
public class ApprovalController {

    private final ApprovalStore approvalStore;

    public ApprovalController(ApprovalStore approvalStore) {
        this.approvalStore = approvalStore;
    }

    @PostMapping("/{requestId}")
    @Operation(
            summary = "Resolve an approval request",
            description = "Approves or denies a pending human-in-the-loop approval request."
    )
    @ApiResponse(responseCode = "200", description = "Approval resolved")
    @ApiResponse(responseCode = "404", description = "Approval request not found")
    @ApiResponse(responseCode = "410", description = "Approval request expired")
    public ResponseEntity<Map<String, String>> resolveApproval(
            @PathVariable String requestId,
            @RequestBody ApprovalDecisionRequest request) {

        if (approvalStore.isExpired(requestId)) {
            throw new ApprovalExpiredException(requestId);
        }

        ApprovalRequest pending = approvalStore.getPending(requestId)
                .orElseThrow(() -> new ApprovalExpiredException(requestId));

        ApprovalDecision decision = new ApprovalDecision(requestId, request.decision(), request.reason());
        approvalStore.resolve(requestId, decision);

        return ResponseEntity.ok(Map.of(
                "requestId", requestId,
                "decision", request.decision(),
                "status", "resolved"
        ));
    }

    public record ApprovalDecisionRequest(String decision, String reason) {
    }
}
