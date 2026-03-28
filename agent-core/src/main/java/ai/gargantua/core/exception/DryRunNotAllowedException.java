package ai.gargantua.core.exception;

/**
 * Thrown when a dry-run request is sent but the current environment or profile
 * does not permit dry-run mode.
 */
public class DryRunNotAllowedException extends RuntimeException {

    public DryRunNotAllowedException() {
        super("Dry run is not allowed in this context");
    }
}
