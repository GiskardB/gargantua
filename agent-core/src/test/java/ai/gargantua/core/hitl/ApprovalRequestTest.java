package ai.gargantua.core.hitl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ApprovalRequest")
class ApprovalRequestTest {

    @Test
    @DisplayName("all fields are accessible via record accessors")
    void allFieldsAccessible() {
        Instant expires = Instant.parse("2024-12-01T00:00:00Z");
        Map<String, Object> params = Map.of("amount", 1000, "currency", "EUR");

        ApprovalRequest req = new ApprovalRequest(
                "req-1", "sess-1", "user-1", "transferMoney",
                params, "Transfer 1000 EUR", true, expires
        );

        assertEquals("req-1", req.requestId());
        assertEquals("sess-1", req.sessionId());
        assertEquals("user-1", req.userId());
        assertEquals("transferMoney", req.toolName());
        assertEquals(2, req.parameters().size());
        assertEquals(1000, req.parameters().get("amount"));
        assertEquals("Transfer 1000 EUR", req.message());
        assertTrue(req.dangerous());
        assertEquals(expires, req.expiresAt());
    }

    @Test
    @DisplayName("non-dangerous request")
    void nonDangerous() {
        ApprovalRequest req = new ApprovalRequest(
                "req-2", "s", "u", "readData", Map.of(), "Read data", false, Instant.now()
        );
        assertFalse(req.dangerous());
    }

    @Test
    @DisplayName("empty parameters map")
    void emptyParameters() {
        ApprovalRequest req = new ApprovalRequest(
                "r", "s", "u", "tool", Map.of(), "msg", false, Instant.now()
        );
        assertTrue(req.parameters().isEmpty());
    }

    @Test
    @DisplayName("record equality based on all fields")
    void equality() {
        Instant ts = Instant.parse("2024-01-01T00:00:00Z");
        ApprovalRequest a = new ApprovalRequest("r1", "s", "u", "t", Map.of(), "m", false, ts);
        ApprovalRequest b = new ApprovalRequest("r1", "s", "u", "t", Map.of(), "m", false, ts);
        ApprovalRequest c = new ApprovalRequest("r2", "s", "u", "t", Map.of(), "m", false, ts);

        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("allows null fields")
    void nullFields() {
        ApprovalRequest req = new ApprovalRequest(null, null, null, null, null, null, false, null);
        assertNull(req.requestId());
        assertNull(req.parameters());
        assertNull(req.expiresAt());
    }
}
