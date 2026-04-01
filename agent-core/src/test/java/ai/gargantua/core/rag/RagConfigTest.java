package ai.gargantua.core.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RagConfig")
class RagConfigTest {

    @Test
    @DisplayName("canonical constructor stores all fields")
    void canonicalConstructor() {
        RagConfig config = new RagConfig("my-knowledge-base", 10, 0.5);

        assertEquals("my-knowledge-base", config.knowledgeBase());
        assertEquals(10, config.maxResults());
        assertEquals(0.5, config.minScore(), 0.001);
    }

    @Test
    @DisplayName("convenience constructor defaults maxResults to 5 and minScore to 0.3")
    void convenienceConstructorDefaults() {
        RagConfig config = new RagConfig("kb-default");

        assertEquals("kb-default", config.knowledgeBase());
        assertEquals(5, config.maxResults());
        assertEquals(0.3, config.minScore(), 0.001);
    }

    @Test
    @DisplayName("custom maxResults and minScore override defaults")
    void customValues() {
        RagConfig config = new RagConfig("kb", 20, 0.8);
        assertEquals(20, config.maxResults());
        assertEquals(0.8, config.minScore(), 0.001);
    }

    @Test
    @DisplayName("record equality based on all fields")
    void equality() {
        RagConfig a = new RagConfig("kb", 5, 0.3);
        RagConfig b = new RagConfig("kb", 5, 0.3);
        RagConfig c = new RagConfig("kb", 10, 0.3);

        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("convenience constructor produces equal result to canonical with same defaults")
    void convenienceEqualsCanonical() {
        RagConfig fromConvenience = new RagConfig("kb");
        RagConfig fromCanonical = new RagConfig("kb", 5, 0.3);
        assertEquals(fromConvenience, fromCanonical);
    }

    @Test
    @DisplayName("zero maxResults and zero minScore are valid")
    void zeroValues() {
        RagConfig config = new RagConfig("kb", 0, 0.0);
        assertEquals(0, config.maxResults());
        assertEquals(0.0, config.minScore(), 0.001);
    }

    @Test
    @DisplayName("allows null knowledge base name")
    void nullKnowledgeBase() {
        RagConfig config = new RagConfig(null, 5, 0.3);
        assertNull(config.knowledgeBase());
    }

    @Test
    @DisplayName("minScore at boundary value 1.0")
    void maxMinScore() {
        RagConfig config = new RagConfig("kb", 5, 1.0);
        assertEquals(1.0, config.minScore(), 0.001);
    }
}
