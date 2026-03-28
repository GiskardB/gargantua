package ai.gargantua.core.exception;

import java.util.Map;

public class GuardrailBlockedException extends RuntimeException {

    private final String guardrailName;
    private final Map<String, Object> metadata;

    public GuardrailBlockedException(String guardrailName, String reason, Map<String, Object> metadata) {
        super(reason);
        this.guardrailName = guardrailName;
        this.metadata = metadata;
    }

    public String getGuardrailName() {
        return guardrailName;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
