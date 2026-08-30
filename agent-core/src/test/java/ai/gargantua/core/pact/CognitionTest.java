package ai.gargantua.core.pact;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Cognition")
class CognitionTest {

    @Test
    @DisplayName("none() is empty")
    void noneIsEmpty() {
        assertTrue(Cognition.none().isEmpty());
    }

    @Test
    @DisplayName("null collections default to empty, not null")
    void nullCollectionsDefaulted() {
        Cognition cognition = new Cognition(null, null, null, null);
        assertEquals(Set.of(), cognition.modalities());
        assertEquals(Set.of(), cognition.capabilities());
        assertTrue(cognition.isEmpty());
    }

    @Test
    @DisplayName("declaring only modalities is not empty")
    void declaringModalitiesIsNotEmpty() {
        Cognition cognition = new Cognition(Set.of("text"), null, null, null);
        assertFalse(cognition.isEmpty());
    }

    @Test
    @DisplayName("collections are defensively copied")
    void collectionsAreDefensivelyCopied() {
        Cognition cognition = new Cognition(Set.of("text"), Set.of("reasoning"), null, null);
        assertThrows(UnsupportedOperationException.class, () -> cognition.modalities().add("image"));
    }
}
