package ai.gargantua.example.cookbook.enrichers;

import ai.gargantua.core.orchestrator.ContextEnricher;
import ai.gargantua.core.orchestrator.EnricherContext;
import org.springframework.stereotype.Component;

/**
 * Injects the user's dietary profile (restrictions, allergies, cuisines) into
 * the system prompt so the LLM can personalise recipes without the user having
 * to repeat the same constraints each turn.
 *
 * <p>Returns mock data keyed on {@code userId}; a production agent would resolve
 * this from a profile database or knowledge memory.</p>
 */
@Component
public class DietaryProfileEnricher implements ContextEnricher {

    @Override
    public String sectionName() {
        return "user_dietary_profile";
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
                    Diet: omnivore
                    Allergies: none
                    Dislikes: cilantro, blue cheese
                    Cuisine preferences: italian, japanese
                    Cooking skill: intermediate
                    Default servings: 2
                    """;
            case "user-002" -> """
                    Name: Sarah Chen
                    Diet: vegetarian
                    Allergies: peanuts
                    Dislikes: olives
                    Cuisine preferences: indian, thai, mexican
                    Cooking skill: advanced
                    Default servings: 4
                    """;
            case "user-003" -> """
                    Name: Mike Torres
                    Diet: vegan, gluten-free
                    Allergies: tree nuts
                    Dislikes: tofu (texture)
                    Cuisine preferences: mediterranean, middle-eastern
                    Cooking skill: beginner
                    Default servings: 1
                    """;
            default -> """
                    Diet: omnivore
                    Allergies: none
                    Cuisine preferences: any
                    Cooking skill: intermediate
                    Default servings: 2
                    """;
        };
    }
}
