package ai.gargantua.core.pact;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InterfaceEndpoint")
class InterfaceEndpointTest {

    @Test
    @DisplayName("protocol is required")
    void protocolRequired() {
        assertThrows(IllegalArgumentException.class, () -> new InterfaceEndpoint(null, "https://x", null));
        assertThrows(IllegalArgumentException.class, () -> new InterfaceEndpoint(" ", "https://x", null));
    }

    @Test
    @DisplayName("endpoint is required")
    void endpointRequired() {
        assertThrows(IllegalArgumentException.class, () -> new InterfaceEndpoint("a2a", null, null));
        assertThrows(IllegalArgumentException.class, () -> new InterfaceEndpoint("a2a", " ", null));
    }

    @Test
    @DisplayName("version is optional")
    void versionOptional() {
        InterfaceEndpoint endpoint = new InterfaceEndpoint("a2a", "https://example.com/a2a", null);
        assertNull(endpoint.version());
    }
}
