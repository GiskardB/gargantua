package ai.gargantua.core.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Exception classes")
class ExceptionsTest {

    // ── ApprovalExpiredException ──────────────────────────────────────────────

    @Nested
    @DisplayName("ApprovalExpiredException")
    class ApprovalExpiredExceptionTests {

        @Test
        @DisplayName("stores requestId and formats message")
        void storesRequestId() {
            ApprovalExpiredException ex = new ApprovalExpiredException("req-123");
            assertEquals("req-123", ex.getRequestId());
            assertTrue(ex.getMessage().contains("req-123"));
            assertTrue(ex.getMessage().contains("expired"));
        }

        @Test
        @DisplayName("throws NullPointerException for null requestId")
        void nullRequestId() {
            assertThrows(NullPointerException.class, () -> new ApprovalExpiredException(null));
        }

        @Test
        @DisplayName("is a RuntimeException")
        void isRuntimeException() {
            assertInstanceOf(RuntimeException.class, new ApprovalExpiredException("r"));
        }
    }

    // ── DryRunNotAllowedException ────────────────────────────────────────────

    @Nested
    @DisplayName("DryRunNotAllowedException")
    class DryRunNotAllowedExceptionTests {

        @Test
        @DisplayName("has fixed message")
        void fixedMessage() {
            DryRunNotAllowedException ex = new DryRunNotAllowedException();
            assertEquals("Dry run is not allowed in this context", ex.getMessage());
        }

        @Test
        @DisplayName("is a RuntimeException")
        void isRuntimeException() {
            assertInstanceOf(RuntimeException.class, new DryRunNotAllowedException());
        }
    }


    // ── GuardrailBlockedException ───────────────────────────────────────────

    @Nested
    @DisplayName("GuardrailBlockedException")
    class GuardrailBlockedExceptionTests {

        @Test
        @DisplayName("stores guardrailName, reason as message, and metadata")
        void storesFields() {
            Map<String, Object> meta = Map.of("pattern", "SSN", "count", 2);
            GuardrailBlockedException ex = new GuardrailBlockedException("pii-check", "PII detected", meta);

            assertEquals("pii-check", ex.getGuardrailName());
            assertEquals("PII detected", ex.getMessage());
            assertEquals(2, ex.getMetadata().size());
            assertEquals("SSN", ex.getMetadata().get("pattern"));
        }

        @Test
        @DisplayName("null metadata defaults to empty map")
        void nullMetadata() {
            GuardrailBlockedException ex = new GuardrailBlockedException("guard", "reason", null);
            assertNotNull(ex.getMetadata());
            assertTrue(ex.getMetadata().isEmpty());
        }

        @Test
        @DisplayName("metadata is immutable copy")
        void immutableMetadata() {
            Map<String, Object> meta = Map.of("key", "value");
            GuardrailBlockedException ex = new GuardrailBlockedException("g", "r", meta);
            assertThrows(UnsupportedOperationException.class, () -> ex.getMetadata().put("new", "val"));
        }

        @Test
        @DisplayName("throws NullPointerException for null guardrailName")
        void nullGuardrailName() {
            assertThrows(NullPointerException.class, () -> new GuardrailBlockedException(null, "r", Map.of()));
        }
    }

    // ── RateLimitExceededException ──────────────────────────────────────────

    @Nested
    @DisplayName("RateLimitExceededException")
    class RateLimitExceededExceptionTests {

        @Test
        @DisplayName("stores userId and limit, formats message")
        void storesFields() {
            RateLimitExceededException ex = new RateLimitExceededException("user1", 100);
            assertEquals("user1", ex.getUserId());
            assertEquals(100, ex.getLimit());
            assertTrue(ex.getMessage().contains("user1"));
            assertTrue(ex.getMessage().contains("100"));
        }

        @Test
        @DisplayName("throws NullPointerException for null userId")
        void nullUserId() {
            assertThrows(NullPointerException.class, () -> new RateLimitExceededException(null, 10));
        }

        @Test
        @DisplayName("zero limit is valid")
        void zeroLimit() {
            RateLimitExceededException ex = new RateLimitExceededException("u", 0);
            assertEquals(0, ex.getLimit());
        }
    }

    // ── SchemaValidationException ───────────────────────────────────────────

    @Nested
    @DisplayName("SchemaValidationException")
    class SchemaValidationExceptionTests {

        @Test
        @DisplayName("stores skillName and validationError, formats message")
        void storesFields() {
            SchemaValidationException ex = new SchemaValidationException("greeting", "missing field 'name'");
            assertEquals("greeting", ex.getSkillName());
            assertEquals("missing field 'name'", ex.getValidationError());
            assertTrue(ex.getMessage().contains("greeting"));
            assertTrue(ex.getMessage().contains("missing field 'name'"));
        }

        @Test
        @DisplayName("throws NullPointerException for null skillName")
        void nullSkillName() {
            assertThrows(NullPointerException.class, () -> new SchemaValidationException(null, "err"));
        }

        @Test
        @DisplayName("throws NullPointerException for null validationError")
        void nullValidationError() {
            assertThrows(NullPointerException.class, () -> new SchemaValidationException("sk", null));
        }
    }

    // ── SkillNotFoundException ───────────────────────────────────────────────

    @Nested
    @DisplayName("SkillNotFoundException")
    class SkillNotFoundExceptionTests {

        @Test
        @DisplayName("formats message with skill name")
        void formatsMessage() {
            SkillNotFoundException ex = new SkillNotFoundException("unknown-skill");
            assertTrue(ex.getMessage().contains("unknown-skill"));
            assertTrue(ex.getMessage().contains("not found"));
        }

        @Test
        @DisplayName("throws NullPointerException for null skillName")
        void nullSkillName() {
            assertThrows(NullPointerException.class, () -> new SkillNotFoundException(null));
        }

        @Test
        @DisplayName("is a RuntimeException")
        void isRuntimeException() {
            assertInstanceOf(RuntimeException.class, new SkillNotFoundException("sk"));
        }
    }

    // ── TokenBudgetExceededException ────────────────────────────────────────

    @Nested
    @DisplayName("TokenBudgetExceededException")
    class TokenBudgetExceededExceptionTests {

        @Test
        @DisplayName("stores fixedTokens and maxTokens, formats message")
        void storesFields() {
            TokenBudgetExceededException ex = new TokenBudgetExceededException(10000, 8000);
            assertEquals(10000, ex.getFixedTokens());
            assertEquals(8000, ex.getMaxTokens());
            assertTrue(ex.getMessage().contains("10000"));
            assertTrue(ex.getMessage().contains("8000"));
        }

        @Test
        @DisplayName("zero values are valid")
        void zeroValues() {
            TokenBudgetExceededException ex = new TokenBudgetExceededException(0, 0);
            assertEquals(0, ex.getFixedTokens());
            assertEquals(0, ex.getMaxTokens());
        }

        @Test
        @DisplayName("is a RuntimeException")
        void isRuntimeException() {
            assertInstanceOf(RuntimeException.class, new TokenBudgetExceededException(1, 0));
        }

        @Test
        @DisplayName("message describes the budget exceeded scenario")
        void messageContent() {
            TokenBudgetExceededException ex = new TokenBudgetExceededException(5000, 4096);
            assertTrue(ex.getMessage().contains("exceeded"));
        }
    }
}
