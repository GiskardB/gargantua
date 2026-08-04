package ai.gargantua.core.secret;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SecretPlaceholders")
class SecretPlaceholdersTest {

    private static final SecretResolver RESOLVER =
            name -> "github-token".equals(name) ? Optional.of("ghp_real") : Optional.empty();

    @Test
    @DisplayName("expands a secret placeholder")
    void expandsSecret() {
        assertEquals("Bearer ghp_real",
                SecretPlaceholders.expand("Bearer ${secrets.github-token}", RESOLVER, key -> null));
    }

    @Test
    @DisplayName("expands an env placeholder from the supplied lookup")
    void expandsEnv() {
        assertEquals("host=db1",
                SecretPlaceholders.expand("host=${env.DB_HOST}", RESOLVER,
                        key -> "DB_HOST".equals(key) ? "db1" : null));
    }

    @Test
    @DisplayName("leaves an unresolved placeholder verbatim rather than blanking it")
    void unresolvedLeftVerbatim() {
        assertEquals("${secrets.missing}",
                SecretPlaceholders.expand("${secrets.missing}", RESOLVER, key -> null));
    }

    @Test
    @DisplayName("expands several placeholders in one value")
    void expandsMultiple() {
        assertEquals("ghp_real@db1",
                SecretPlaceholders.expand("${secrets.github-token}@${env.DB_HOST}", RESOLVER,
                        key -> "db1"));
    }

    @Test
    @DisplayName("returns input unchanged when it holds no placeholder")
    void noPlaceholderPassesThrough() {
        assertEquals("plain", SecretPlaceholders.expand("plain", RESOLVER, key -> null));
    }

    @Test
    @DisplayName("null input returns null")
    void nullPassesThrough() {
        assertNull(SecretPlaceholders.expand(null, RESOLVER, key -> null));
    }

    @Test
    @DisplayName("a dollar sign inside a resolved value is not reinterpreted")
    void dollarInValueNotReinterpreted() {
        assertEquals("a$1b",
                SecretPlaceholders.expand("${secrets.d}", name -> Optional.of("a$1b"), key -> null));
    }

    @Test
    @DisplayName("unknown namespace is left untouched")
    void unknownNamespaceUntouched() {
        assertEquals("${other.value}",
                SecretPlaceholders.expand("${other.value}", RESOLVER, key -> "x"));
    }

    @Test
    @DisplayName("hasPlaceholder detects a remaining reference")
    void hasPlaceholderDetects() {
        assertTrue(SecretPlaceholders.hasPlaceholder("x ${secrets.a} y"));
        assertTrue(SecretPlaceholders.hasPlaceholder("${env.A}"));
        assertFalse(SecretPlaceholders.hasPlaceholder("plain"));
        assertFalse(SecretPlaceholders.hasPlaceholder(null));
    }

    @Test
    @DisplayName("expandAll expands every map value")
    void expandAllExpandsValues() {
        Map<String, String> expanded = SecretPlaceholders.expandAll(
                Map.of("TOKEN", "${secrets.github-token}"), RESOLVER);

        assertEquals("ghp_real", expanded.get("TOKEN"));
    }

    @Test
    @DisplayName("expandAll returns an empty map for null or empty input")
    void expandAllHandlesEmpty() {
        assertTrue(SecretPlaceholders.expandAll(null, RESOLVER).isEmpty());
        assertTrue(SecretPlaceholders.expandAll(Map.of(), RESOLVER).isEmpty());
    }

    @Test
    @DisplayName("empty resolver resolves nothing")
    void emptyResolverResolvesNothing() {
        assertTrue(SecretResolver.empty().resolve("anything").isEmpty());
    }
}
