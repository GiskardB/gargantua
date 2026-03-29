package ai.gargantua.core.exception;

import java.io.Serial;

/**
 * Thrown when a dry-run request is sent but the current environment or profile
 * does not permit dry-run mode.
 */
public class DryRunNotAllowedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public DryRunNotAllowedException() {
        super("Dry run is not allowed in this context");
    }
}
