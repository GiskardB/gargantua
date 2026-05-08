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

/**
 * Output guardrail that redacts PII (emails, IBANs, phone numbers) from the agent's response.
 * Runs first in the output pipeline (order=10) to sanitize before other transformations.
 * Disabled by default; enable via {@code agent.guardrail.output.pii-masking-enabled=true}.
 */
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

        Map<String, String> piiMap = null;
        if (ctx.inputAttributes() != null) {
            Object raw = ctx.inputAttributes().get("pii_map");
            if (raw instanceof Map<?, ?> m && !m.isEmpty()) {
                piiMap = (Map<String, String>) m;
            }
        }

        if (piiMap != null) {
            // Input phase already masked PII and stashed {placeholder → original}.
            // Restore the originals so the user sees their own data back, replacing
            // longer placeholders first to avoid partial substitution.
            String restored = response;
            var entries = new java.util.ArrayList<>(piiMap.entrySet());
            entries.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));
            for (Map.Entry<String, String> entry : entries) {
                String placeholder = entry.getKey();
                String original = entry.getValue();
                if (placeholder == null || placeholder.isEmpty() || original == null) continue;
                if (restored.contains(placeholder)) {
                    restored = restored.replace(placeholder, original);
                }
            }
            return new GuardrailOutputResult(GuardrailVerdict.PASS, restored, null, name());
        }

        // No input pii_map — apply regex masking on the LLM output as a safety net.
        String masked = response;
        masked = maskPattern(masked, EMAIL_PATTERN, "[EMAIL_REDACTED]");
        masked = maskPattern(masked, IBAN_PATTERN, "[IBAN_REDACTED]");
        masked = maskPattern(masked, PHONE_PATTERN, "[PHONE_REDACTED]");
        return new GuardrailOutputResult(GuardrailVerdict.PASS, masked, null, name());
    }

    private String maskPattern(String text, Pattern pattern, String replacement) {
        Matcher matcher = pattern.matcher(text);
        return matcher.replaceAll(replacement);
    }
}
