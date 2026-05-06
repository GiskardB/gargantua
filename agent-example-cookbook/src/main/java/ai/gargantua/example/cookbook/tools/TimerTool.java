package ai.gargantua.example.cookbook.tools;

import ai.gargantua.core.tool.AgentTool;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Tiny utility tool: schedule a kitchen timer. The actual notification is out
 * of scope for the example — the tool just returns the computed expiry instant.
 * Demonstrates a plain {@code @AgentTool} with no caching, retries, or approval —
 * the simplest useful tool shape.
 */
@Component
public class TimerTool {

    /** A scheduled timer: name, when it'll fire, and its duration. */
    public record TimerHandle(String name, Instant firesAt, int minutes) {}

    @AgentTool(description = """
            Schedules a named kitchen timer for the given number of minutes.
            Use when the user says "set a timer for X minutes" or "remind me in N
            minutes when the pasta is done". Returns the timer handle so the
            user can read it back.
            """)
    public TimerHandle setTimer(String name, int minutes) {
        int safe = Math.max(1, Math.min(minutes, 24 * 60));
        return new TimerHandle(
                name == null || name.isBlank() ? "kitchen-timer" : name,
                Instant.now().plus(safe, ChronoUnit.MINUTES),
                safe
        );
    }
}
