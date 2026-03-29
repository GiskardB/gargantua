package ai.gargantua.autoconfigure;

import ai.gargantua.core.guardrail.GuardrailInputContext;
import ai.gargantua.core.guardrail.GuardrailOutputContext;
import ai.gargantua.core.guardrail.GuardrailOutputResult;
import ai.gargantua.core.guardrail.GuardrailPipelineResult;
import ai.gargantua.core.guardrail.GuardrailResult;
import ai.gargantua.core.guardrail.GuardrailVerdict;
import ai.gargantua.core.guardrail.InputGuardrail;
import ai.gargantua.core.guardrail.OutputGuardrail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Executes input and output guardrails in a Chain of Responsibility pattern.
 * Input guardrails are evaluated in Spring {@code @Order} sequence; the first BLOCK
 * short-circuits the pipeline. Output guardrails chain transformations on the response
 * text, each receiving the output of the previous guardrail.
 *
 * @see ai.gargantua.core.guardrail.InputGuardrail
 * @see ai.gargantua.core.guardrail.OutputGuardrail
 */
@Component
public class GuardrailPipeline {

    private static final Logger log = LoggerFactory.getLogger(GuardrailPipeline.class);

    private final List<InputGuardrail> inputGuardrails;
    private final List<OutputGuardrail> outputGuardrails;
    private final AgentProperties properties;

    public GuardrailPipeline(List<InputGuardrail> inputGuardrails,
                             List<OutputGuardrail> outputGuardrails,
                             AgentProperties properties) {
        this.inputGuardrails = inputGuardrails;
        this.outputGuardrails = outputGuardrails;
        this.properties = properties;
    }

    /**
     * Iterate input guardrails in order. Skip disabled guardrails. Stop at first BLOCK.
     */
    public GuardrailPipelineResult checkInput(GuardrailInputContext ctx) {
        List<GuardrailResult> results = new ArrayList<>();

        for (InputGuardrail guardrail : inputGuardrails) {
            if (!guardrail.isEnabled(properties)) {
                log.debug("Skipping disabled input guardrail: {}", guardrail.name());
                continue;
            }

            GuardrailResult result = guardrail.check(ctx);
            results.add(result);

            if (result.verdict() == GuardrailVerdict.BLOCK) {
                log.info("Input blocked by guardrail '{}': {}", guardrail.name(), result.reason());
                return GuardrailPipelineResult.blocked(guardrail.name(), result.reason(), results);
            }

            if (result.verdict() == GuardrailVerdict.WARN) {
                log.warn("Warning from guardrail '{}': {}", guardrail.name(), result.reason());
            }
        }

        return GuardrailPipelineResult.passed(results);
    }

    /**
     * Iterate output guardrails, chaining transformations on the response text.
     */
    public String processOutput(GuardrailOutputContext ctx) {
        GuardrailOutputContext current = ctx;

        for (OutputGuardrail guardrail : outputGuardrails) {
            if (!guardrail.isEnabled(properties)) {
                log.debug("Skipping disabled output guardrail: {}", guardrail.name());
                continue;
            }

            GuardrailOutputResult result = guardrail.process(current);

            if (result.processedResponse() != null) {
                current = current.withRawResponse(result.processedResponse());
            }

            if (result.verdict() == GuardrailVerdict.BLOCK) {
                log.warn("Output blocked by guardrail '{}': {}", guardrail.name(), result.reason());
                return result.processedResponse() != null ? result.processedResponse() : current.rawResponse();
            }
        }

        return current.rawResponse();
    }
}
