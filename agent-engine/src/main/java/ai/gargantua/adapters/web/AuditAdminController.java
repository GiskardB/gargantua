package ai.gargantua.adapters.web;

import ai.gargantua.core.audit.AuditEvent;
import ai.gargantua.core.audit.AuditStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Admin REST endpoints for querying the audit trail.
 * Provides compliance-oriented queries by user, tenant, session, and time range.
 */
@RestController
@RequestMapping("/api/admin/audit")
@Tag(name = "Admin \u2014 Audit")
public class AuditAdminController {

    private final AuditStore auditStore;

    public AuditAdminController(AuditStore auditStore) {
        this.auditStore = auditStore;
    }

    @GetMapping
    @Operation(summary = "List audit events by user",
               description = "Returns audit events for a specific user within a time range.")
    @ApiResponse(responseCode = "200", description = "Audit events")
    public ResponseEntity<List<AuditEvent>> getByUser(
            @Parameter(description = "User ID to filter by")
            @RequestParam String userId,
            @Parameter(description = "Start date (ISO-8601)")
            @RequestParam(required = false) String from,
            @Parameter(description = "End date (ISO-8601)")
            @RequestParam(required = false) String to,
            @Parameter(description = "Maximum number of events to return")
            @RequestParam(defaultValue = "50") int limit) {
        Instant fromInstant = parseFrom(from);
        Instant toInstant = parseTo(to);
        return ResponseEntity.ok(auditStore.findByUser(userId, fromInstant, toInstant, limit));
    }

    @GetMapping("/tenant")
    @Operation(summary = "List audit events by tenant",
               description = "Returns audit events for a specific tenant within a time range.")
    @ApiResponse(responseCode = "200", description = "Audit events")
    public ResponseEntity<List<AuditEvent>> getByTenant(
            @Parameter(description = "Tenant ID to filter by")
            @RequestParam String tenantId,
            @Parameter(description = "Start date (ISO-8601)")
            @RequestParam(required = false) String from,
            @Parameter(description = "End date (ISO-8601)")
            @RequestParam(required = false) String to,
            @Parameter(description = "Maximum number of events to return")
            @RequestParam(defaultValue = "50") int limit) {
        Instant fromInstant = parseFrom(from);
        Instant toInstant = parseTo(to);
        return ResponseEntity.ok(auditStore.findByTenant(tenantId, fromInstant, toInstant, limit));
    }

    @GetMapping("/session/{sessionId}")
    @Operation(summary = "List audit events by session",
               description = "Returns all audit events for a specific session.")
    @ApiResponse(responseCode = "200", description = "Audit events")
    public ResponseEntity<List<AuditEvent>> getBySession(
            @Parameter(description = "Session ID")
            @PathVariable String sessionId) {
        return ResponseEntity.ok(auditStore.findBySession(sessionId));
    }

    @GetMapping("/{eventId}")
    @Operation(summary = "Get audit event by ID",
               description = "Returns a single audit event by its unique ID.")
    @ApiResponse(responseCode = "200", description = "Audit event")
    @ApiResponse(responseCode = "404", description = "Event not found")
    public ResponseEntity<AuditEvent> getById(
            @Parameter(description = "Event ID")
            @PathVariable String eventId) {
        return auditStore.findById(eventId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/count")
    @Operation(summary = "Count audit events",
               description = "Returns the count of audit events within a time range.")
    @ApiResponse(responseCode = "200", description = "Event count")
    public ResponseEntity<Long> count(
            @Parameter(description = "Start date (ISO-8601)")
            @RequestParam(required = false) String from,
            @Parameter(description = "End date (ISO-8601)")
            @RequestParam(required = false) String to) {
        Instant fromInstant = parseFrom(from);
        Instant toInstant = parseTo(to);
        return ResponseEntity.ok(auditStore.countByTimeRange(fromInstant, toInstant));
    }

    private static Instant parseFrom(String value) {
        if (value == null) return Instant.now().minus(30, ChronoUnit.DAYS);
        try {
            return Instant.parse(value);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Invalid 'from' date (expected ISO-8601): " + value);
        }
    }

    private static Instant parseTo(String value) {
        if (value == null) return Instant.now();
        try {
            return Instant.parse(value);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Invalid 'to' date (expected ISO-8601): " + value);
        }
    }
}
