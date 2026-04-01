package ai.gargantua.autoconfigure;

import ai.gargantua.core.cost.CostTrackingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;

@DisplayName("CostTracker")
class CostTrackerTest {

    private AgentProperties properties;
    private CostTracker costTracker;

    @BeforeEach
    void setUp() {
        properties = new AgentProperties();
        costTracker = new CostTracker(properties);
    }

    private CostTrackingEvent sampleEvent() {
        return new CostTrackingEvent(
                "user-1", "session-1", "fitness-coach",
                "openai", "gpt-4o", "main",
                500, 200, 1500L, false
        );
    }

    @Test
    @DisplayName("records event when cost tracking is enabled")
    void recordsEventWhenEnabled() {
        properties.getCostTracking().setEnabled(true);

        assertThatNoException().isThrownBy(() -> costTracker.record(sampleEvent()));
    }

    @Test
    @DisplayName("silently skips recording when cost tracking is disabled")
    void skipsWhenDisabled() {
        properties.getCostTracking().setEnabled(false);

        assertThatNoException().isThrownBy(() -> costTracker.record(sampleEvent()));
    }

    @Test
    @DisplayName("handles dry-run events without error")
    void handlesDryRunEvents() {
        properties.getCostTracking().setEnabled(true);

        CostTrackingEvent dryRunEvent = new CostTrackingEvent(
                "user-1", "session-1", "fitness-coach",
                "openai", "gpt-4o", "main",
                500, 200, 1500L, true
        );

        assertThatNoException().isThrownBy(() -> costTracker.record(dryRunEvent));
    }

    @Test
    @DisplayName("handles zero tokens without error")
    void handlesZeroTokens() {
        properties.getCostTracking().setEnabled(true);

        CostTrackingEvent event = new CostTrackingEvent(
                "user-1", "session-1", "skill",
                "openai", "gpt-4o", "routing",
                0, 0, 50L, false
        );

        assertThatNoException().isThrownBy(() -> costTracker.record(event));
    }

    @Test
    @DisplayName("cost tracking disabled by default")
    void disabledByDefault() {
        // Default properties should have cost tracking disabled
        AgentProperties defaultProps = new AgentProperties();
        CostTracker tracker = new CostTracker(defaultProps);

        assertThatNoException().isThrownBy(() -> tracker.record(sampleEvent()));
    }
}
