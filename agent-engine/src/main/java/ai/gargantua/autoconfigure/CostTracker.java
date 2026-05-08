package ai.gargantua.autoconfigure;

import ai.gargantua.core.cost.CostTrackingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Placeholder cost tracker that logs events. Real implementations
 * will persist to a store and compute cost based on pricing config.
 */
@Component
public class CostTracker {

    private static final Logger log = LoggerFactory.getLogger(CostTracker.class);

    private final AgentProperties properties;

    public CostTracker(AgentProperties properties) {
        this.properties = properties;
    }

    /**
     * Record a cost tracking event.
     */
    public void record(CostTrackingEvent event) {
        if (!properties.getCostTracking().isEnabled()) {
            return;
        }

        log.info("Cost event: user={}, skill={}, provider={}, model={}, " +
                        "inputTokens={}, outputTokens={}, durationMs={}, dryRun={}",
                event.userId(),
                event.skillName(),
                event.provider(),
                event.model(),
                event.inputTokens(),
                event.outputTokens(),
                event.durationMs(),
                event.dryRun()
        );
    }

    /**
     * Estimate USD cost for a single LLM call. Reads {@code agent.cost-tracking.pricing}
     * with relaxed key resolution so both the documented nested form
     * ({@code <provider>.<model>.input-per-1k-tokens}) and flat colon-form
     * ({@code <provider>:<model>:input-per-1k-tokens}) are accepted.
     * Returns {@code 0.0} when cost tracking is disabled or no pricing entry matches.
     */
    public double estimateUsd(String provider, String model, int inputTokens, int outputTokens) {
        if (!properties.getCostTracking().isEnabled()) {
            return 0.0;
        }
        Double inputPer1k = lookupPrice(provider, model, "input-per-1k-tokens");
        Double outputPer1k = lookupPrice(provider, model, "output-per-1k-tokens");
        if (inputPer1k == null && outputPer1k == null) {
            return 0.0;
        }
        double inputCost = inputPer1k != null ? (inputTokens / 1000.0) * inputPer1k : 0.0;
        double outputCost = outputPer1k != null ? (outputTokens / 1000.0) * outputPer1k : 0.0;
        return inputCost + outputCost;
    }

    private Double lookupPrice(String provider, String model, String suffix) {
        var pricing = properties.getCostTracking().getPricing();
        if (pricing == null || pricing.isEmpty()) return null;
        // Try, in order: provider.model.suffix, provider:model:suffix, model.suffix, model:suffix
        for (String key : candidateKeys(provider, model, suffix)) {
            Double v = pricing.get(key);
            if (v != null) return v;
        }
        return null;
    }

    private static java.util.List<String> candidateKeys(String provider, String model, String suffix) {
        var keys = new java.util.ArrayList<String>();
        if (provider != null && !provider.isBlank() && model != null && !model.isBlank()) {
            keys.add(provider + "." + model + "." + suffix);
            keys.add(provider + ":" + model + ":" + suffix);
        }
        if (model != null && !model.isBlank()) {
            keys.add(model + "." + suffix);
            keys.add(model + ":" + suffix);
        }
        return keys;
    }
}
