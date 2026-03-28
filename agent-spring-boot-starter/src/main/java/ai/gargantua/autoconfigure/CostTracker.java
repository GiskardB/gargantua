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
}
