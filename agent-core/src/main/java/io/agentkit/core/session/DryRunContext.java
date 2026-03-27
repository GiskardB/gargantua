package io.agentkit.core.session;

import java.util.Map;

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
