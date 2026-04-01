package ai.gargantua.autoconfigure;

import ai.gargantua.autoconfigure.guardrails.SchemaValidatorGuardrail;
import ai.gargantua.core.guardrail.GuardrailOutputContext;
import ai.gargantua.core.guardrail.GuardrailOutputResult;
import ai.gargantua.core.guardrail.GuardrailVerdict;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SchemaValidatorGuardrail")
class SchemaValidatorGuardrailTest {

    private static final String SIMPLE_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "name": { "type": "string" },
                "age": { "type": "integer" }
              },
              "required": ["name", "age"]
            }
            """;

    private AgentProperties propsWithSchemaValidation(boolean enabled) {
        AgentProperties props = new AgentProperties();
        props.getGuardrail().getOutput().setSchemaValidationEnabled(enabled);
        return props;
    }

    private SkillMeta skillWithSchema() {
        return new SkillMeta("test-skill", "desc", "1.0.0", true, true, "test",
                SkillSource.FILESYSTEM, Set.of());
    }

    private SkillMeta skillWithoutSchema() {
        return new SkillMeta("test-skill", "desc", "1.0.0", true, false, "test",
                SkillSource.FILESYSTEM, Set.of());
    }

    private GuardrailOutputContext ctx(String response, SkillMeta skill, Map<String, Object> attrs) {
        return new GuardrailOutputContext(response, "user1", "session1", skill, attrs);
    }

    private Map<String, Object> attrsWithSchema(String schema) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("output_schema", schema);
        return attrs;
    }

    // --- name() ---

    @Test
    @DisplayName("name() returns 'schema-validator'")
    void name_returnsSchemaValidator() {
        var guardrail = new SchemaValidatorGuardrail(new AgentProperties());
        assertThat(guardrail.name()).isEqualTo("schema-validator");
    }

    // --- isEnabled() ---

    @Test
    @DisplayName("isEnabled() returns true when schema validation is enabled")
    void isEnabled_trueWhenEnabled() {
        AgentProperties props = propsWithSchemaValidation(true);
        var guardrail = new SchemaValidatorGuardrail(props);
        assertThat(guardrail.isEnabled(props)).isTrue();
    }

    @Test
    @DisplayName("isEnabled() returns false when schema validation is disabled")
    void isEnabled_falseWhenDisabled() {
        AgentProperties props = propsWithSchemaValidation(false);
        var guardrail = new SchemaValidatorGuardrail(props);
        assertThat(guardrail.isEnabled(props)).isFalse();
    }

    @Test
    @DisplayName("isEnabled() is true by default")
    void isEnabled_trueByDefault() {
        var guardrail = new SchemaValidatorGuardrail(new AgentProperties());
        assertThat(guardrail.isEnabled(new AgentProperties())).isTrue();
    }

    @Test
    @DisplayName("isEnabled() falls back to injected props for non-AgentProperties argument")
    void isEnabled_fallsBackToInjectedProps() {
        AgentProperties props = propsWithSchemaValidation(false);
        var guardrail = new SchemaValidatorGuardrail(props);
        assertThat(guardrail.isEnabled("other")).isFalse();
    }

    // --- process() — skip conditions ---

    @Test
    @DisplayName("process() passes when activated skill is null")
    void process_passesWhenSkillIsNull() {
        var guardrail = new SchemaValidatorGuardrail(propsWithSchemaValidation(true));

        GuardrailOutputResult result = guardrail.process(ctx("{\"name\":\"test\"}", null, null));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
    }

    @Test
    @DisplayName("process() passes when skill has no schema (hasSchema=false)")
    void process_passesWhenSkillHasNoSchema() {
        var guardrail = new SchemaValidatorGuardrail(propsWithSchemaValidation(true));

        GuardrailOutputResult result = guardrail.process(
                ctx("{\"name\":\"test\"}", skillWithoutSchema(), attrsWithSchema(SIMPLE_SCHEMA)));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
    }

    @Test
    @DisplayName("process() passes when response is null")
    void process_passesWhenResponseIsNull() {
        var guardrail = new SchemaValidatorGuardrail(propsWithSchemaValidation(true));

        GuardrailOutputResult result = guardrail.process(
                ctx(null, skillWithSchema(), attrsWithSchema(SIMPLE_SCHEMA)));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
    }

    @Test
    @DisplayName("process() passes when response is blank")
    void process_passesWhenResponseIsBlank() {
        var guardrail = new SchemaValidatorGuardrail(propsWithSchemaValidation(true));

        GuardrailOutputResult result = guardrail.process(
                ctx("   ", skillWithSchema(), attrsWithSchema(SIMPLE_SCHEMA)));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
    }

    @Test
    @DisplayName("process() passes when no output_schema is in attributes")
    void process_passesWhenNoSchemaInAttributes() {
        var guardrail = new SchemaValidatorGuardrail(propsWithSchemaValidation(true));

        GuardrailOutputResult result = guardrail.process(
                ctx("{\"name\":\"test\",\"age\":25}", skillWithSchema(), new HashMap<>()));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
    }

    @Test
    @DisplayName("process() passes when input attributes are null")
    void process_passesWhenInputAttributesNull() {
        var guardrail = new SchemaValidatorGuardrail(propsWithSchemaValidation(true));

        GuardrailOutputResult result = guardrail.process(
                ctx("{\"name\":\"test\",\"age\":25}", skillWithSchema(), null));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
    }

    // --- process() — JSON extraction ---

    @Test
    @DisplayName("process() passes when response is not JSON (plain text)")
    void process_passesWhenResponseIsNotJson() {
        var guardrail = new SchemaValidatorGuardrail(propsWithSchemaValidation(true));

        GuardrailOutputResult result = guardrail.process(
                ctx("This is just plain text", skillWithSchema(), attrsWithSchema(SIMPLE_SCHEMA)));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
        assertThat(result.reason()).contains("does not contain JSON");
    }

    @Test
    @DisplayName("process() extracts JSON from markdown code blocks")
    void process_extractsJsonFromMarkdownCodeBlock() {
        var guardrail = new SchemaValidatorGuardrail(propsWithSchemaValidation(true));

        String response = "Here is the result:\n```json\n{\"name\":\"Alice\",\"age\":30}\n```\nDone!";
        GuardrailOutputResult result = guardrail.process(
                ctx(response, skillWithSchema(), attrsWithSchema(SIMPLE_SCHEMA)));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
    }

    @Test
    @DisplayName("process() extracts raw JSON starting with {")
    void process_extractsRawJsonObject() {
        var guardrail = new SchemaValidatorGuardrail(propsWithSchemaValidation(true));

        GuardrailOutputResult result = guardrail.process(
                ctx("{\"name\":\"Bob\",\"age\":42}", skillWithSchema(), attrsWithSchema(SIMPLE_SCHEMA)));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
    }

    @Test
    @DisplayName("process() extracts raw JSON starting with [")
    void process_extractsRawJsonArray() {
        var guardrail = new SchemaValidatorGuardrail(propsWithSchemaValidation(true));

        String arraySchema = """
                {
                  "type": "array",
                  "items": { "type": "string" }
                }
                """;
        GuardrailOutputResult result = guardrail.process(
                ctx("[\"a\",\"b\"]", skillWithSchema(), attrsWithSchema(arraySchema)));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
    }

    // --- process() — valid JSON against schema ---

    @Test
    @DisplayName("process() passes for valid JSON matching schema")
    void process_passesForValidJson() {
        var guardrail = new SchemaValidatorGuardrail(propsWithSchemaValidation(true));

        GuardrailOutputResult result = guardrail.process(
                ctx("{\"name\":\"Charlie\",\"age\":25}", skillWithSchema(), attrsWithSchema(SIMPLE_SCHEMA)));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
        assertThat(result.reason()).isNull();
    }

    // --- process() — invalid JSON against schema ---

    @Test
    @DisplayName("process() blocks for JSON missing required field")
    void process_blocksForMissingRequiredField() {
        var guardrail = new SchemaValidatorGuardrail(propsWithSchemaValidation(true));

        GuardrailOutputResult result = guardrail.process(
                ctx("{\"name\":\"Charlie\"}", skillWithSchema(), attrsWithSchema(SIMPLE_SCHEMA)));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.BLOCK);
        assertThat(result.reason()).contains("Schema validation failed");
    }

    @Test
    @DisplayName("process() blocks for JSON with wrong type")
    void process_blocksForWrongType() {
        var guardrail = new SchemaValidatorGuardrail(propsWithSchemaValidation(true));

        GuardrailOutputResult result = guardrail.process(
                ctx("{\"name\":\"Charlie\",\"age\":\"not-a-number\"}", skillWithSchema(), attrsWithSchema(SIMPLE_SCHEMA)));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.BLOCK);
        assertThat(result.reason()).contains("Schema validation failed");
    }

    // --- process() — malformed JSON ---

    @Test
    @DisplayName("process() passes with error reason for malformed JSON")
    void process_passesForMalformedJson() {
        var guardrail = new SchemaValidatorGuardrail(propsWithSchemaValidation(true));

        GuardrailOutputResult result = guardrail.process(
                ctx("{broken json", skillWithSchema(), attrsWithSchema(SIMPLE_SCHEMA)));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
        assertThat(result.reason()).contains("Schema validation error");
    }

    // --- process() — malformed schema ---

    @Test
    @DisplayName("process() passes with error reason for malformed schema")
    void process_passesForMalformedSchema() {
        var guardrail = new SchemaValidatorGuardrail(propsWithSchemaValidation(true));

        GuardrailOutputResult result = guardrail.process(
                ctx("{\"name\":\"test\",\"age\":1}", skillWithSchema(), attrsWithSchema("{not valid schema")));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
        assertThat(result.reason()).contains("Schema validation error");
    }

    @Test
    @DisplayName("process() guardrail name is always 'schema-validator'")
    void process_resultHasCorrectGuardrailName() {
        var guardrail = new SchemaValidatorGuardrail(propsWithSchemaValidation(true));

        GuardrailOutputResult result = guardrail.process(
                ctx("{\"name\":\"test\",\"age\":1}", skillWithSchema(), attrsWithSchema(SIMPLE_SCHEMA)));
        assertThat(result.guardrailName()).isEqualTo("schema-validator");
    }
}
