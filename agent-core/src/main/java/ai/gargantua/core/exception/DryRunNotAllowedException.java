package ai.gargantua.core.exception;

public class DryRunNotAllowedException extends RuntimeException {

    public DryRunNotAllowedException() {
        super("Dry run is not allowed in this context");
    }
}
