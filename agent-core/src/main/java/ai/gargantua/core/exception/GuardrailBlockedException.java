package ai.gargantua.core.exception;

import java.io.Serial;
import java.util.Map;
import java.util.Objects;

/**
 * Thrown when an input guardrail returns a BLOCK verdict, aborting the request.
 * Handled by the exception handler to return a 422 response with details.
 */
public class GuardrailBlockedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String guardrailName;
    private final Map<String, Object> metadata;

    public GuardrailBlockedException(String guardrailName, String reason, Map<String, Object> metadata) {
        super(reason);
        this.guardrailName = Objects.requireNonNull(guardrailName, "guardrailName must not be null");
        this.metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    public String getGuardrailName() {
        return guardrailName;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
