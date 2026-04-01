package ai.gargantua.autoconfigure.guardrails;

import ai.gargantua.autoconfigure.AgentProperties;
import ai.gargantua.core.guardrail.GuardrailInputContext;
import ai.gargantua.core.guardrail.GuardrailResult;
import ai.gargantua.core.guardrail.InputGuardrail;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Input guardrail that detects and masks PII (emails, IBANs, phone numbers) in user messages.
 * Always returns PASS -- masking is informational, not blocking. The PII map is stored
 * in context attributes for potential de-anonymization by the output guardrail.
 * Disabled by default; enable via {@code agent.guardrail.input.pii-masking-enabled=true}.
 */
@Component
@Order(40)
public class PiiInputGuardrail implements InputGuardrail {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");
    private static final Pattern IBAN_PATTERN =
            Pattern.compile("\\b[A-Z]{2}\\d{2}[A-Z0-9]{4}\\d{7}([A-Z0-9]?){0,16}\\b");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("\\+?\\d[\\d\\s\\-]{7,}\\d");

    private final AgentProperties agentProperties;

    public PiiInputGuardrail(AgentProperties agentProperties) {
        this.agentProperties = agentProperties;
    }

    @Override
    public String name() {
        return "pii-input-masking";
    }

    @Override
    public boolean isEnabled(Object props) {
        if (props instanceof AgentProperties ap) {
            return ap.getGuardrail().getInput().isPiiMaskingEnabled();
        }
        return agentProperties.getGuardrail().getInput().isPiiMaskingEnabled();
    }

    @Override
    public GuardrailResult check(GuardrailInputContext ctx) {
        if (ctx.userMessage() == null || ctx.userMessage().isBlank()) {
            return GuardrailResult.pass(name());
        }

        var piiMap = new HashMap<String, String>();
        var masked = ctx.userMessage();
        int counter = 0;

        // Mask emails
        Matcher emailMatcher = EMAIL_PATTERN.matcher(masked);
        while (emailMatcher.find()) {
            var original = emailMatcher.group();
            var placeholder = "[EMAIL_%d]".formatted(counter);
            piiMap.put(placeholder, original);
            masked = masked.replace(original, placeholder);
            counter++;
        }

        // Mask IBANs
        Matcher ibanMatcher = IBAN_PATTERN.matcher(masked);
        while (ibanMatcher.find()) {
            var original = ibanMatcher.group();
            var placeholder = "[IBAN_%d]".formatted(counter);
            piiMap.put(placeholder, original);
            masked = masked.replace(original, placeholder);
            counter++;
        }

        // Mask phones
        Matcher phoneMatcher = PHONE_PATTERN.matcher(masked);
        while (phoneMatcher.find()) {
            var original = phoneMatcher.group();
            var placeholder = "[PHONE_%d]".formatted(counter);
            piiMap.put(placeholder, original);
            masked = masked.replace(original, placeholder);
            counter++;
        }

        // Store pii_map in context attributes for potential de-anonymization later
        if (!piiMap.isEmpty()) {
            ctx.attributes().put("pii_map", piiMap);
            ctx.attributes().put("original_message", ctx.userMessage());
            ctx.attributes().put("masked_message", masked);
        }

        // Always returns PASS - masking is informational, not blocking
        return GuardrailResult.pass(name())
                .withMetadata("pii_detected", !piiMap.isEmpty())
                .withMetadata("pii_count", piiMap.size());
    }
}
