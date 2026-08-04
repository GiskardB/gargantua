package ai.gargantua.core.workload;

/**
 * The runtime a workload requires in order to execute.
 *
 * <p>Bundles and runtime images version independently. A purely declarative bundle
 * runs on the stock image; a workload that needs bespoke Java tooling names a custom
 * image built in library mode. Recording the requirement in the manifest lets the
 * Deployment Manager schedule the workload onto a compatible runtime instead of
 * failing at startup.</p>
 *
 * @param image      container image required to execute this workload, or {@code null}
 *                   to accept the platform default runtime
 * @param minVersion minimum Gargantua runtime version, or {@code null} for no floor
 */
public record RuntimeSpec(String image, String minVersion) {

    /** Accepts whatever runtime the platform provides — the common case. */
    public static RuntimeSpec platformDefault() {
        return new RuntimeSpec(null, null);
    }

    /** Whether this workload pins a specific runtime image. */
    public boolean hasCustomImage() {
        return image != null && !image.isBlank();
    }
}
