package ai.gargantua.adapters.web;

import ai.gargantua.adapters.cost.MongoCostTrackingRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
import java.util.Map;

/**
 * Admin REST endpoint for querying token usage and cost reports.
 * Aggregates data from MongoDB by skill, provider, and time range.
 */
@RestController
@RequestMapping("/api/admin/costs")
@ConditionalOnBean(MongoCostTrackingRepository.class)
@Tag(
        name = "Admin \u2014 Costs",
        description = "Aggregated LLM cost metrics from `MongoCostTrackingRepository`. Every chat "
                + "request emits one or more `CostTrackingEvent` records (one per LLM call: routing, "
                + "primary, summarizer); these endpoints expose roll-ups by skill / provider / user / "
                + "day. USD figures are computed from `agent.cost-tracking.pricing`."
)
public class CostAdminController {

    private final MongoCostTrackingRepository costRepository;

    public CostAdminController(MongoCostTrackingRepository costRepository) {
        this.costRepository = costRepository;
    }

    @GetMapping("/summary")
    @Operation(summary = "Get cost summary", description = "Returns aggregated cost summary by skill and provider.")
    @ApiResponse(responseCode = "200", description = "Cost summary")
    public ResponseEntity<List<Map<String, Object>>> getSummary(
            @Parameter(description = "Start date (ISO-8601)")
            @RequestParam(required = false) String from,
            @Parameter(description = "End date (ISO-8601)")
            @RequestParam(required = false) String to) {
        Instant fromInstant = parseFrom(from);
        Instant toInstant = parseTo(to);
        return ResponseEntity.ok(costRepository.findSummary(fromInstant, toInstant));
    }

    @GetMapping("/by-skill")
    @Operation(summary = "Get costs by skill", description = "Returns cost breakdown aggregated by skill.")
    @ApiResponse(responseCode = "200", description = "Costs by skill")
    public ResponseEntity<List<Map<String, Object>>> getBySkill(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        Instant fromInstant = parseFrom(from);
        Instant toInstant = parseTo(to);
        return ResponseEntity.ok(costRepository.findBySkill(fromInstant, toInstant));
    }

    @GetMapping("/by-user/{userId}")
    @Operation(summary = "Get costs by user", description = "Returns token usage records for a specific user.")
    @ApiResponse(responseCode = "200", description = "User costs")
    public ResponseEntity<List<MongoCostTrackingRepository.TokenUsageDocument>> getByUser(
            @PathVariable String userId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        Instant fromInstant = parseFrom(from);
        Instant toInstant = parseTo(to);
        return ResponseEntity.ok(costRepository.findByUser(userId, fromInstant, toInstant));
    }

    @GetMapping("/by-provider")
    @Operation(summary = "Get costs by provider", description = "Returns cost breakdown aggregated by provider.")
    @ApiResponse(responseCode = "200", description = "Costs by provider")
    public ResponseEntity<List<Map<String, Object>>> getByProvider(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        Instant fromInstant = parseFrom(from);
        Instant toInstant = parseTo(to);
        // Reuse summary which groups by skill+provider
        return ResponseEntity.ok(costRepository.findSummary(fromInstant, toInstant));
    }

    @GetMapping("/daily")
    @Operation(summary = "Get daily costs", description = "Returns cost breakdown aggregated by day.")
    @ApiResponse(responseCode = "200", description = "Daily costs")
    public ResponseEntity<List<Map<String, Object>>> getDaily(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        Instant fromInstant = parseFrom(from);
        Instant toInstant = parseTo(to);
        return ResponseEntity.ok(costRepository.findDaily(fromInstant, toInstant));
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
