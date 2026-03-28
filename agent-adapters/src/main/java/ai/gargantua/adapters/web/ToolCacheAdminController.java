package ai.gargantua.adapters.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/tool-cache")
@Tag(name = "Admin \u2014 Tool Cache")
public class ToolCacheAdminController {

    private static final String CACHE_KEY_PREFIX = "tool-cache:";

    private final StringRedisTemplate redisTemplate;

    public ToolCacheAdminController(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/stats")
    @Operation(summary = "Get tool cache stats", description = "Returns statistics about the tool result cache.")
    @ApiResponse(responseCode = "200", description = "Cache statistics")
    public ResponseEntity<Map<String, Object>> getStats() {
        Set<String> keys = redisTemplate.keys(CACHE_KEY_PREFIX + "*");
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalEntries", keys != null ? keys.size() : 0);
        stats.put("keyPrefix", CACHE_KEY_PREFIX);

        // Group by tool name
        Map<String, Integer> byTool = new HashMap<>();
        if (keys != null) {
            for (String key : keys) {
                String toolPart = key.substring(CACHE_KEY_PREFIX.length());
                String toolName = toolPart.contains(":") ? toolPart.substring(0, toolPart.indexOf(':')) : toolPart;
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
        Set<String> keys = redisTemplate.keys(CACHE_KEY_PREFIX + toolName + ":*");
        long deleted = 0;
        if (keys != null && !keys.isEmpty()) {
            deleted = redisTemplate.delete(keys);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("toolName", toolName);
        result.put("entriesDeleted", deleted);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping
    @Operation(summary = "Clear all tool caches", description = "Clears all cached tool results.")
    @ApiResponse(responseCode = "200", description = "All tool caches cleared")
    public ResponseEntity<Map<String, Object>> clearAllCaches() {
        Set<String> keys = redisTemplate.keys(CACHE_KEY_PREFIX + "*");
        long deleted = 0;
        if (keys != null && !keys.isEmpty()) {
            deleted = redisTemplate.delete(keys);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("entriesDeleted", deleted);
        return ResponseEntity.ok(result);
    }
}
