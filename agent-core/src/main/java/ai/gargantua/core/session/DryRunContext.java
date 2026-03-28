package ai.gargantua.core.session;

import java.util.Map;

/**
 * Controls dry-run mode for the orchestrator. When active, tool calls return
 * stubbed responses and memory is not persisted. Used by the eval runner and
 * testing workflows.
 *
 * @param active    whether dry-run mode is enabled
 * @param toolStubs map of tool name to stubbed return value (used instead of real execution)
 */
public record DryRunContext(
        boolean active,
        Map<String, Object> toolStubs
) {

    public static DryRunContext inactive() {
        return new DryRunContext(false, Map.of());
    }

    public static DryRunContext active(Map<String, Object> stubs) {
        return new DryRunContext(true, stubs);
    }
}
