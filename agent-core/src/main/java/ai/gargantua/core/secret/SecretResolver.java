package ai.gargantua.core.secret;

import java.util.Optional;

/**
 * Resolves secret references declared in a workload manifest.
 *
 * <p>Bundles are immutable, versioned and signed, which means they cannot carry
 * credentials. Instead a manifest references a secret <em>by name</em> — for example
 * {@code ${secrets.github-token}} in an {@link ai.gargantua.core.mcp.McpServerSpec}
 * environment entry — and the runtime resolves it at startup through this port.</p>
 *
 * <p>Keeping resolution behind an interface leaves the storage decision open: the
 * default runtime implementation reads process environment variables injected by the
 * Deployment Manager, while deployments that require it can plug in a vault-backed
 * resolver without any change to the bundle format.</p>
 *
 * @see SecretPlaceholders
 */
@FunctionalInterface
public interface SecretResolver {

    /**
     * Returns the value bound to {@code name}, or empty when the secret is unknown.
     * Implementations must not throw for a missing secret — callers decide whether
     * absence is fatal.
     *
     * @param name secret name without the {@code ${secrets.}} wrapper
     */
    Optional<String> resolve(String name);

    /** Resolver that knows no secrets; useful in tests and in fully public bundles. */
    static SecretResolver empty() {
        return name -> Optional.empty();
    }
}
