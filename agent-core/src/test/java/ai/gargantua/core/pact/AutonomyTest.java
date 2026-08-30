package ai.gargantua.core.pact;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Autonomy")
class AutonomyTest {

    @Test
    @DisplayName("ofLevel resolves every wire-format level 0-4")
    void ofLevelResolvesEveryLevel() {
        assertEquals(Autonomy.PASSIVE, Autonomy.ofLevel(0));
        assertEquals(Autonomy.ASSISTIVE, Autonomy.ofLevel(1));
        assertEquals(Autonomy.RECOMMENDING, Autonomy.ofLevel(2));
        assertEquals(Autonomy.EXECUTING, Autonomy.ofLevel(3));
        assertEquals(Autonomy.AUTONOMOUS, Autonomy.ofLevel(4));
    }

    @Test
    @DisplayName("level() round-trips back to the wire-format integer")
    void levelRoundTrips() {
        for (Autonomy autonomy : Autonomy.values()) {
            assertEquals(autonomy, Autonomy.ofLevel(autonomy.level()));
        }
    }

    @Test
    @DisplayName("a level outside 0-4 is rejected")
    void outOfRangeLevelRejected() {
        assertThrows(IllegalArgumentException.class, () -> Autonomy.ofLevel(-1));
        assertThrows(IllegalArgumentException.class, () -> Autonomy.ofLevel(5));
    }
}
