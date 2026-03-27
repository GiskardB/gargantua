package io.agentkit.autoconfigure;

import io.agentkit.autoconfigure.guardrails.PiiInputGuardrail;
import io.agentkit.core.guardrail.GuardrailInputContext;
import io.agentkit.core.guardrail.GuardrailResult;
import io.agentkit.core.guardrail.GuardrailVerdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PiiInputGuardrailTest {

    private PiiInputGuardrail guardrail;

    @BeforeEach
    void setUp() {
        AgentProperties props = new AgentProperties();
        props.getGuardrail().getInput().setPiiMaskingEnabled(true);
        guardrail = new PiiInputGuardrail(props);
    }

    private GuardrailInputContext ctx(String message) {
        return new GuardrailInputContext(message, "user1", "session1", null, new HashMap<>());
    }

    @Test
    void alwaysReturnsPASS() {
        GuardrailResult result = guardrail.check(ctx("Contact me at test@example.com"));
        assertEquals(GuardrailVerdict.PASS, result.verdict());
    }

    @Test
    void masksEmail() {
        Map<String, Object> attributes = new HashMap<>();
        GuardrailInputContext context = new GuardrailInputContext(
                "Email me at john@example.com please", "user1", "session1", null, attributes);
        GuardrailResult result = guardrail.check(context);

        assertEquals(GuardrailVerdict.PASS, result.verdict());
        assertTrue((boolean) result.metadata().get("pii_detected"));
        assertEquals(1, (int) result.metadata().get("pii_count"));

        // Check pii_map was stored in attributes
        @SuppressWarnings("unchecked")
        Map<String, String> piiMap = (Map<String, String>) attributes.get("pii_map");
        assertNotNull(piiMap);
        assertTrue(piiMap.containsValue("john@example.com"));

        String masked = (String) attributes.get("masked_message");
        assertNotNull(masked);
        assertFalse(masked.contains("john@example.com"));
        assertTrue(masked.contains("[EMAIL_"));
    }

    @Test
    void masksIBAN() {
        Map<String, Object> attributes = new HashMap<>();
        GuardrailInputContext context = new GuardrailInputContext(
                "My IBAN is DE89370400440532013000", "user1", "session1", null, attributes);
        guardrail.check(context);

        @SuppressWarnings("unchecked")
        Map<String, String> piiMap = (Map<String, String>) attributes.get("pii_map");
        assertNotNull(piiMap);
        assertTrue(piiMap.containsValue("DE89370400440532013000"));
    }

    @Test
    void masksPhoneNumber() {
        Map<String, Object> attributes = new HashMap<>();
        GuardrailInputContext context = new GuardrailInputContext(
                "Call me at +1 555-123-4567", "user1", "session1", null, attributes);
        guardrail.check(context);

        @SuppressWarnings("unchecked")
        Map<String, String> piiMap = (Map<String, String>) attributes.get("pii_map");
        assertNotNull(piiMap);
        assertFalse(piiMap.isEmpty());
    }

    @Test
    void storesPiiMapInAttributes() {
        Map<String, Object> attributes = new HashMap<>();
        GuardrailInputContext context = new GuardrailInputContext(
                "Email: a@b.com and phone: +1 555-000-1234", "user1", "session1", null, attributes);
        GuardrailResult result = guardrail.check(context);

        assertTrue((boolean) result.metadata().get("pii_detected"));
        assertTrue((int) result.metadata().get("pii_count") >= 2);
    }

    @Test
    void noPiiDetected() {
        GuardrailResult result = guardrail.check(ctx("Hello, how are you?"));
        assertFalse((boolean) result.metadata().get("pii_detected"));
        assertEquals(0, (int) result.metadata().get("pii_count"));
    }
}
