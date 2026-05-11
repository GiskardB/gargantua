package ai.gargantua.adapters.web;

import ai.gargantua.autoconfigure.ToolResultCache;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Admin REST endpoint for inspecting and clearing cached tool results.
 * Cache keys are prefixed with {@code tool-cache:}.
 *
 * <p>Since 1.2.6 this controller goes through the {@link ToolResultCache}
 * abstraction rather than talking to Redis directly. It works identically
 * whether the backend is Redis (production) or the in-memory map (embedded
 * mode).</p>
 */
@RestController
@RequestMapping("/api/admin/tool-cache")
@Tag(name = "Admin — Tool Cache")
public class ToolCacheAdminController {

    private static final String CACHE_KEY_PREFIX = "tool-cache:";

    private final ToolResultCache cache;

    public ToolCacheAdminController(ToolResultCache cache) {
        this.cache = cache;
    }

    @GetMapping("/stats")
    @Operation(summary = "Get tool cache stats", description = "Returns statistics about the tool result cache.")
    @ApiResponse(responseCode = "200", description = "Cache statistics")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalEntries", cache.size());
        stats.put("keyPrefix", CACHE_KEY_PREFIX);

        // Group by tool name. Key layout: tool-cache:<scope>:<tool>:[<userOrSession>:]<argsHash>
        // The tool name is the segment immediately after the scope prefix
        // (or the second segment after the scope for USER/SESSION which carry an id).
        Map<String, Integer> byTool = new HashMap<>();
        for (String key : cache.keys()) {
            String toolName = extractToolName(key);
            if (toolName != null) {
                byTool.merge(toolName, 1, Integer::sum);
            }
        }
        stats.put("byTool", byTool);
        return ResponseEntity.ok(stats);
    }

    @DeleteMapping("/{toolName}")
    @Operation(summary = "Clear tool cache", description = "Clears cached results for a specific tool.")
    @ApiResponse(responseCode = "200", description = "Tool cache cleared")
    public ResponseEntity<Map<String, Object>> clearToolCache(@PathVariable String toolName) {
        int deleted = cache.clear(toolName);
        Map<String, Object> result = new HashMap<>();
        result.put("toolName", toolName);
        result.put("entriesDeleted", deleted);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping
    @Operation(summary = "Clear all tool caches", description = "Clears all cached tool results.")
    @ApiResponse(responseCode = "200", description = "All tool caches cleared")
    public ResponseEntity<Map<String, Object>> clearAllCaches() {
        int deleted = cache.clear();
        Map<String, Object> result = new HashMap<>();
        result.put("entriesDeleted", deleted);
        return ResponseEntity.ok(result);
    }

    /**
     * Extract the tool name from a {@code tool-cache:…} key. The tool name is
     * "the segment that is not 'global', not 'user', not 'session', not an
     * identifier following them, and not the trailing args hash". In practice
     * the layout is:
     * <ul>
     *   <li>{@code tool-cache:global:<tool>:<hash>}</li>
     *   <li>{@code tool-cache:user:<userId>:<tool>:<hash>}</li>
     *   <li>{@code tool-cache:session:<sessionId>:<tool>:<hash>}</li>
     * </ul>
     * so the tool name is the second-to-last segment.
     */
    private static String extractToolName(String key) {
        if (!key.startsWith(CACHE_KEY_PREFIX)) return null;
        String[] parts = key.substring(CACHE_KEY_PREFIX.length()).split(":");
        if (parts.length < 3) return null;
        return parts[parts.length - 2];
    }
}
