package ai.gargantua.example.enrichers;

import ai.gargantua.core.orchestrator.ContextEnricher;
import ai.gargantua.core.orchestrator.EnricherContext;
import org.springframework.stereotype.Component;

/**
 * Injects the current user's fitness profile into the system prompt so the LLM
 * can personalise responses (e.g. adjust difficulty, respect restrictions).
 * Runs for every skill — returns mock data keyed on the userId.
 */
@Component
public class FitnessProfileEnricher implements ContextEnricher {

    @Override
    public String sectionName() {
        return "user_fitness_profile";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public String enrich(EnricherContext ctx) {
        if (ctx.userId() == null || ctx.userId().isBlank()) {
            return "";
        }

        return switch (ctx.userId()) {
            case "user-001" -> """
                    Name: Alex Johnson
                    Fitness level: intermediate
                    Goal: muscle gain
                    Restrictions: none
                    Last workout: 2 days ago
                    Weekly target: 4 sessions
                    Preferred equipment: barbell, dumbbells, cable machine
                    """;
            case "user-002" -> """
                    Name: Sarah Chen
                    Fitness level: advanced
                    Goal: fat loss
                    Restrictions: knee injury — no heavy squats
                    Last workout: yesterday
                    Weekly target: 5 sessions
                    Preferred equipment: full gym
                    """;
            case "user-003" -> """
                    Name: Mike Torres
                    Fitness level: beginner
                    Goal: general fitness
                    Restrictions: lactose intolerant, lower back pain
                    Last workout: 4 days ago
                    Weekly target: 3 sessions
                    Preferred equipment: bodyweight, resistance bands
                    """;
            default -> """
                    Fitness level: intermediate
                    Goal: muscle gain
                    Restrictions: none
                    Last workout: 2 days ago
                    Weekly target: 4 sessions
                    """;
        };
    }
}
