package ai.gargantua.autoconfigure.guardrails;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import ai.gargantua.autoconfigure.AgentProperties;
import ai.gargantua.core.guardrail.GuardrailOutputContext;
import ai.gargantua.core.guardrail.GuardrailOutputResult;
import ai.gargantua.core.guardrail.GuardrailVerdict;
import ai.gargantua.core.guardrail.OutputGuardrail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Output guardrail that validates JSON responses against the skill's output schema
 * using JSON Schema Draft-07. Only activates for skills with {@code hasSchema=true}.
 * Extracts JSON from markdown code blocks or raw JSON responses before validation.
 * Enabled by default; configure via {@code agent.guardrail.output.schema-validation-enabled}.
 */
@Component
@Order(40)
public class SchemaValidatorGuardrail implements OutputGuardrail {

    private static final Logger log = LoggerFactory.getLogger(SchemaValidatorGuardrail.class);

    private final AgentProperties agentProperties;
    private final ObjectMapper objectMapper;

    public SchemaValidatorGuardrail(AgentProperties agentProperties) {
        this.agentProperties = agentProperties;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String name() {
        return "schema-validator";
    }

    @Override
    public boolean isEnabled(Object props) {
        if (props instanceof AgentProperties ap) {
            return ap.getGuardrail().getOutput().isSchemaValidationEnabled();
        }
        return agentProperties.getGuardrail().getOutput().isSchemaValidationEnabled();
    }

    @Override
    public GuardrailOutputResult process(GuardrailOutputContext ctx) {
        // Only active if the skill has an output schema
        if (ctx.activatedSkill() == null || !ctx.activatedSkill().hasSchema()) {
            return new GuardrailOutputResult(GuardrailVerdict.PASS, ctx.rawResponse(), null, name());
        }

        String response = ctx.rawResponse();
        if (response == null || response.isBlank()) {
            return new GuardrailOutputResult(GuardrailVerdict.PASS, response, null, name());
        }

        // Try to extract JSON from the response (it may be wrapped in markdown code blocks)
        String jsonContent = extractJson(response);
        if (jsonContent == null) {
            return new GuardrailOutputResult(GuardrailVerdict.PASS, response,
                    "Response does not contain JSON; schema validation skipped", name());
        }

        // For now we need the schema to be provided via inputAttributes
        Object schemaObj = ctx.inputAttributes() != null ? ctx.inputAttributes().get("output_schema") : null;
        if (schemaObj == null) {
            return new GuardrailOutputResult(GuardrailVerdict.PASS, response, null, name());
        }

        try {
            String schemaStr = schemaObj.toString();
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
            JsonSchema schema = factory.getSchema(schemaStr);
            JsonNode jsonNode = objectMapper.readTree(jsonContent);

            Set<ValidationMessage> errors = schema.validate(jsonNode);
            if (errors.isEmpty()) {
                return new GuardrailOutputResult(GuardrailVerdict.PASS, response, null, name());
            }

            var errorDetails = errors.stream()
                    .map(ValidationMessage::getMessage)
                    .collect(Collectors.joining("; "));
            log.warn("Schema validation failed: {}", errorDetails);

            return new GuardrailOutputResult(GuardrailVerdict.BLOCK, response,
                    "Schema validation failed: %s".formatted(errorDetails), name());

        } catch (Exception e) {
            log.error("Error during schema validation", e);
            return new GuardrailOutputResult(GuardrailVerdict.PASS, response,
                    "Schema validation error: %s".formatted(e.getMessage()), name());
        }
    }

    private String extractJson(String text) {
        // Try to find JSON in markdown code blocks
        int codeStart = text.indexOf("```json");
        if (codeStart >= 0) {
            int contentStart = text.indexOf('\n', codeStart) + 1;
            int codeEnd = text.indexOf("```", contentStart);
            if (codeEnd > contentStart) {
                return text.substring(contentStart, codeEnd).strip();
            }
        }

        // Try to find raw JSON (starts with { or [)
        String trimmed = text.strip();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed;
        }

        return null;
    }
}
