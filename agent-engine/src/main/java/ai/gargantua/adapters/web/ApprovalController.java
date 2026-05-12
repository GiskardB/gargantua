package ai.gargantua.adapters.web;

import ai.gargantua.core.exception.ApprovalExpiredException;
import ai.gargantua.core.hitl.ApprovalDecision;
import ai.gargantua.core.hitl.ApprovalRequest;
import ai.gargantua.core.hitl.ApprovalStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST endpoint for resolving human-in-the-loop approval requests.
 * A human reviewer calls this to approve or deny a pending tool execution.
 */
@RestController
@RequestMapping("/api/agent/approval")
@Tag(
        name = "HITL — Approvals",
        description = "Resolve human-in-the-loop approval requests raised by tools annotated with "
                + "`@RequiresApproval`. The streaming chat endpoint surfaces the pending approval id "
                + "via the SSE `approval_required` event; a human reviewer (or upstream UI) then POSTs "
                + "their decision here."
)
public class ApprovalController {

    private final ApprovalStore approvalStore;

    public ApprovalController(ApprovalStore approvalStore) {
        this.approvalStore = approvalStore;
    }

    @PostMapping("/{requestId}")
    @Operation(
            summary = "Resolve a pending approval request",
            description = """
                    Submit the human reviewer's decision for a tool execution paused by
                    `@RequiresApproval`. The agent stream wakes up as soon as the decision
                    is recorded and either runs the tool (`decision: "approve"`) or surfaces
                    a refusal to the LLM that triggered it (`decision: "deny"`).

                    Each `requestId` is single-shot: a second POST after the request has
                    been resolved or has expired returns 410.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Decision recorded — agent will resume."),
            @ApiResponse(responseCode = "404", description = "No pending approval with this id."),
            @ApiResponse(responseCode = "410", description = "The approval request has expired (TTL elapsed) "
                    + "or was already resolved.")
    })
    public ResponseEntity<Map<String, String>> resolveApproval(
            @Parameter(description = "Approval request id surfaced on the SSE `approval_required` event.",
                    example = "appr-7c1d4e2f-…")
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

    @Schema(description = "Reviewer's decision payload.")
    public record ApprovalDecisionRequest(
            @Schema(description = "`approve` or `deny`. Anything else is treated as a deny by the orchestrator.",
                    allowableValues = {"approve", "deny"},
                    example = "approve",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            String decision,
            @Schema(description = "Optional free-text reason. Persisted on the audit trail for compliance.",
                    example = "Verified with the customer over phone.")
            String reason
    ) {
    }
}
