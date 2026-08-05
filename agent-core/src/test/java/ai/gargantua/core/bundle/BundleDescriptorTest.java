package ai.gargantua.core.bundle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BundleDescriptor")
class BundleDescriptorTest {

    @Test
    @DisplayName("development factory produces an unsigned descriptor")
    void developmentFactoryIsUnsigned() {
        BundleDescriptor descriptor = BundleDescriptor.development("customer-agent", "1.2.0");

        assertFalse(descriptor.isSigned());
        assertNull(descriptor.checksum());
        assertNull(descriptor.runtimeImage());
        assertNotNull(descriptor.createdAt());
    }

    @Test
    @DisplayName("a descriptor carrying a signature reports as signed")
    void signedDescriptorDetected() {
        BundleDescriptor descriptor = new BundleDescriptor("a", "1.0.0", "sha256:abc",
                "sig", null, Instant.now(), Map.of());

        assertTrue(descriptor.isSigned());
    }

    @Test
    @DisplayName("a blank signature does not count as signed")
    void blankSignatureIsNotSigned() {
        BundleDescriptor descriptor = new BundleDescriptor("a", "1.0.0", "sha256:abc",
                "   ", null, Instant.now(), Map.of());

        assertFalse(descriptor.isSigned());
    }

    @Test
    @DisplayName("coordinates combine name and version")
    void coordinates() {
        assertEquals("customer-agent:1.2.0",
                BundleDescriptor.development("customer-agent", "1.2.0").coordinates());
    }

    @Test
    @DisplayName("name is required")
    void nameRequired() {
        assertThrows(IllegalArgumentException.class, () -> BundleDescriptor.development("", "1.0.0"));
    }

    @Test
    @DisplayName("version is required")
    void versionRequired() {
        assertThrows(IllegalArgumentException.class, () -> BundleDescriptor.development("a", ""));
    }

    @Test
    @DisplayName("null labels default to an empty map")
    void nullLabelsDefaultToEmpty() {
        BundleDescriptor descriptor =
                new BundleDescriptor("a", "1.0.0", null, null, null, null, null);

        assertTrue(descriptor.labels().isEmpty());
    }

    @Test
    @DisplayName("labels are immutable")
    void labelsAreImmutable() {
        BundleDescriptor descriptor = new BundleDescriptor("a", "1.0.0", null, null, null,
                Instant.now(), Map.of("env", "prod"));

        assertThrows(UnsupportedOperationException.class, () -> descriptor.labels().put("k", "v"));
    }

    @Test
    @DisplayName("runtime image is preserved for bundles requiring a custom runtime")
    void runtimeImagePreserved() {
        BundleDescriptor descriptor = new BundleDescriptor("a", "1.0.0", null, null,
                "acme/gargantua-runtime:2.1", Instant.now(), Map.of());

        assertEquals("acme/gargantua-runtime:2.1", descriptor.runtimeImage());
    }
}
