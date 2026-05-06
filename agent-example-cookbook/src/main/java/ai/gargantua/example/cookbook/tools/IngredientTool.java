package ai.gargantua.example.cookbook.tools;

import ai.gargantua.core.tool.AgentTool;
import ai.gargantua.core.tool.CacheScope;
import ai.gargantua.core.tool.CacheableToolResult;
import ai.gargantua.core.tool.ToolRetry;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Ingredient lookup and substitution tools. Demonstrates {@code GLOBAL}-scope
 * caching (long TTL — nutrition data is essentially static) and {@code @ToolRetry}
 * for the substitution lookup which would normally hit a flaky external API.
 */
@Component
public class IngredientTool {

    /** Macro-nutrient breakdown per 100 g of ingredient. */
    public record Nutrition(
            String ingredient,
            int kcal,
            double proteinG,
            double carbsG,
            double fatG,
            List<String> allergens
    ) {}

    /** A substitution suggestion with a 1-line rationale. */
    public record Substitute(String original, String replacement, String reason, double ratio) {}

    @AgentTool(description = """
            Returns macronutrients (kcal, protein, carbs, fat) and allergen flags
            per 100 g of the named ingredient. Use whenever the user asks
            "how many calories in X" or "is Y vegan/gluten-free".
            """)
    @CacheableToolResult(ttlSeconds = 86400, keyParams = {"ingredient"}, scope = CacheScope.GLOBAL)
    public Nutrition getNutrition(String ingredient) {
        String key = ingredient == null ? "" : ingredient.toLowerCase(Locale.ROOT).trim();
        return switch (key) {
            case "chicken breast" -> new Nutrition("chicken breast", 165, 31.0, 0.0, 3.6, List.of());
            case "salmon" -> new Nutrition("salmon", 208, 20.0, 0.0, 13.0, List.of("fish"));
            case "egg" -> new Nutrition("egg", 155, 13.0, 1.1, 11.0, List.of("egg"));
            case "milk" -> new Nutrition("milk", 42, 3.4, 5.0, 1.0, List.of("dairy", "lactose"));
            case "almonds" -> new Nutrition("almonds", 579, 21.2, 21.6, 49.9, List.of("nuts"));
            case "wheat flour", "flour" -> new Nutrition("wheat flour", 364, 10.3, 76.3, 1.0, List.of("gluten", "wheat"));
            case "rice" -> new Nutrition("rice", 130, 2.7, 28.2, 0.3, List.of());
            case "olive oil" -> new Nutrition("olive oil", 884, 0.0, 0.0, 100.0, List.of());
            case "chickpeas" -> new Nutrition("chickpeas", 164, 8.9, 27.4, 2.6, List.of());
            case "tofu" -> new Nutrition("tofu", 76, 8.0, 1.9, 4.8, List.of("soy"));
            default -> new Nutrition(ingredient == null ? "unknown" : ingredient, 100, 5.0, 15.0, 3.0, List.of());
        };
    }

    @AgentTool(description = """
            Suggests a substitute for an ingredient given the reason
            (e.g. "vegan", "gluten-free", "no nuts", "out of stock"). Returns
            the replacement plus the conversion ratio (replacementAmount = originalAmount * ratio).
            Use when the user is missing an ingredient or has an allergy/preference conflict.
            """)
    @ToolRetry(maxAttempts = 2, waitDurationMs = 200, retryOn = {IOException.class})
    public Substitute findSubstitute(String ingredient, String reason) {
        String key = ingredient == null ? "" : ingredient.toLowerCase(Locale.ROOT).trim();
        String why = reason == null ? "" : reason.toLowerCase(Locale.ROOT);

        if (why.contains("vegan") || why.contains("dairy")) {
            return switch (key) {
                case "milk" -> new Substitute("milk", "oat milk", "dairy-free, similar consistency", 1.0);
                case "butter" -> new Substitute("butter", "extra-virgin olive oil", "plant-based, slightly less saturated fat", 0.75);
                case "egg" -> new Substitute("egg", "1 tbsp ground flax + 3 tbsp water", "binds in baking", 1.0);
                case "honey" -> new Substitute("honey", "maple syrup", "vegan-friendly, similar sweetness", 1.0);
                default -> new Substitute(key, key + " (vegan)", "use a vegan-labelled equivalent", 1.0);
            };
        }
        if (why.contains("gluten")) {
            return switch (key) {
                case "wheat flour", "flour" -> new Substitute("wheat flour", "gluten-free flour blend", "1:1 GF blend works for most pancakes/muffins", 1.0);
                case "soy sauce" -> new Substitute("soy sauce", "tamari", "naturally gluten-free", 1.0);
                case "spaghetti", "pasta" -> new Substitute("pasta", "rice noodles", "naturally gluten-free, lighter texture", 1.0);
                default -> new Substitute(key, key + " (certified GF)", "look for a certified gluten-free version", 1.0);
            };
        }
        return new Substitute(key, key, "no substitution needed", 1.0);
    }
}
