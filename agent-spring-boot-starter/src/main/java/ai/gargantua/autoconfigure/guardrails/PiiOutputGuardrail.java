package ai.gargantua.autoconfigure.guardrails;

import ai.gargantua.autoconfigure.AgentProperties;
import ai.gargantua.core.guardrail.GuardrailOutputContext;
import ai.gargantua.core.guardrail.GuardrailOutputResult;
import ai.gargantua.core.guardrail.GuardrailVerdict;
import ai.gargantua.core.guardrail.OutputGuardrail;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(10)
public class PiiOutputGuardrail implements OutputGuardrail {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");
    private static final Pattern IBAN_PATTERN =
            Pattern.compile("\\b[A-Z]{2}\\d{2}[A-Z0-9]{4}\\d{7}([A-Z0-9]?){0,16}\\b");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("\\+?\\d[\\d\\s\\-]{7,}\\d");

    private final AgentProperties agentProperties;

    public PiiOutputGuardrail(AgentProperties agentProperties) {
        this.agentProperties = agentProperties;
    }

    @Override
    public String name() {
        return "pii-output-masking";
    }

    @Override
    public boolean isEnabled(Object props) {
        if (props instanceof AgentProperties ap) {
            return ap.getGuardrail().getOutput().isPiiMaskingEnabled();
        }
        return agentProperties.getGuardrail().getOutput().isPiiMaskingEnabled();
    }

    @Override
    @SuppressWarnings("unchecked")
    public GuardrailOutputResult process(GuardrailOutputContext ctx) {
        String response = ctx.rawResponse();
        if (response == null || response.isBlank()) {
            return new GuardrailOutputResult(GuardrailVerdict.PASS, response, null, name());
        }

        String masked = response;
        masked = maskPattern(masked, EMAIL_PATTERN, "[EMAIL_REDACTED]");
        masked = maskPattern(masked, IBAN_PATTERN, "[IBAN_REDACTED]");
        masked = maskPattern(masked, PHONE_PATTERN, "[PHONE_REDACTED]");

        // Optionally de-anonymize if pii_map is available from input attributes
        if (ctx.inputAttributes() != null) {
            Object piiMapObj = ctx.inputAttributes().get("pii_map");
            if (piiMapObj instanceof Map<?, ?> rawMap) {
                Map<String, String> piiMap = (Map<String, String>) rawMap;
                for (Map.Entry<String, String> entry : piiMap.entrySet()) {
                    // If the output contains the placeholder, we can de-anonymize
                    // (typically this would be configurable)
                }
            }
        }

        return new GuardrailOutputResult(GuardrailVerdict.PASS, masked, null, name());
    }

    private String maskPattern(String text, Pattern pattern, String replacement) {
        Matcher matcher = pattern.matcher(text);
        return matcher.replaceAll(replacement);
    }
}
