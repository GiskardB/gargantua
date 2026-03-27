package io.agentkit.core.guardrail;

import java.util.HashMap;
import java.util.Map;

public record GuardrailResult(
        GuardrailVerdict verdict,
        String reason,
        String guardrailName,
        Map<String, Object> metadata
) {

    public static GuardrailResult pass(String name) {
        return new GuardrailResult(GuardrailVerdict.PASS, null, name, Map.of());
    }

    public static GuardrailResult block(String name, String reason) {
        return new GuardrailResult(GuardrailVerdict.BLOCK, reason, name, Map.of());
    }

    public static GuardrailResult warn(String name, String reason) {
        return new GuardrailResult(GuardrailVerdict.WARN, reason, name, Map.of());
    }

    public GuardrailResult withMetadata(String key, Object value) {
        var newMetadata = new HashMap<>(this.metadata);
        newMetadata.put(key, value);
        return new GuardrailResult(this.verdict, this.reason, this.guardrailName, Map.copyOf(newMetadata));
    }
}
