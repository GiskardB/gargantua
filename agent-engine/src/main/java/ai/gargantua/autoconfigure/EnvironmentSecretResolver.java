package ai.gargantua.autoconfigure;

import ai.gargantua.core.secret.SecretResolver;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * Default {@link SecretResolver}: reads secrets from the process environment, where the
 * Deployment Manager is expected to inject them.
 *
 * <p>A reference is tried verbatim first, then in conventional environment-variable form
 * — {@code ${secrets.github-token}} resolves against {@code github-token} and, failing
 * that, {@code GITHUB_TOKEN}. That lets a manifest use readable kebab-case names while
 * deployments keep the usual shouting-snake-case variables.</p>
 *
 * <p>Replace this bean to source secrets from a vault; nothing else has to change,
 * because the bundle format only ever carries the reference.</p>
 */
public class EnvironmentSecretResolver implements SecretResolver {

    private final Function<String, String> environment;

    public EnvironmentSecretResolver() {
        this(System::getenv);
    }

    /** Constructor taking an explicit lookup, for tests. */
    public EnvironmentSecretResolver(Function<String, String> environment) {
        this.environment = environment;
    }

    @Override
    public Optional<String> resolve(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String direct = environment.apply(name);
        if (direct != null && !direct.isBlank()) {
            return Optional.of(direct);
        }
        String normalized = name.replace('-', '_').replace('.', '_').toUpperCase(Locale.ROOT);
        if (normalized.equals(name)) {
            return Optional.empty();
        }
        String fallback = environment.apply(normalized);
        return fallback != null && !fallback.isBlank() ? Optional.of(fallback) : Optional.empty();
    }
}
