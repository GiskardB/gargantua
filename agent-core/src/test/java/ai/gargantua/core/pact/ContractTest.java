package ai.gargantua.core.pact;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Contract")
class ContractTest {

    @Test
    @DisplayName("none() is empty")
    void noneIsEmpty() {
        assertTrue(Contract.none().isEmpty());
    }

    @Test
    @DisplayName("null permissions default to empty, not null")
    void nullPermissionsDefaulted() {
        assertEquals(Set.of(), new Contract(null, null).permissions());
    }

    @Test
    @DisplayName("declaring only autonomy is not empty")
    void declaringAutonomyIsNotEmpty() {
        assertFalse(new Contract(Autonomy.RECOMMENDING, Set.of()).isEmpty());
    }

    @Test
    @DisplayName("declaring only permissions is not empty")
    void declaringPermissionsIsNotEmpty() {
        assertFalse(new Contract(null, Set.of("read_repository")).isEmpty());
    }
}
