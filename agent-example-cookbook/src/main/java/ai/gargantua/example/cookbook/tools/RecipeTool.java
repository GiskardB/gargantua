package ai.gargantua.example.cookbook.tools;

import ai.gargantua.core.tool.AgentTool;
import ai.gargantua.core.tool.CacheScope;
import ai.gargantua.core.tool.CacheableToolResult;
import ai.gargantua.core.tool.ToolRetry;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Recipe generation and search tools. Demonstrates {@code @CacheableToolResult}
 * with both {@code USER} and {@code GLOBAL} scopes, and a parallelizable search tool
 * with {@code @ToolRetry}.
 */
@Component
public class RecipeTool {

    /** A single recipe step. */
    public record RecipeStep(int order, String instruction, int durationMinutes) {}

    /** A single recipe ingredient with amount and unit. */
    public record RecipeIngredient(String name, double amount, String unit) {}

    /** A recipe with metadata, ingredients, steps and rough nutrition. */
    public record Recipe(
            String name,
            int servings,
            int prepTimeMin,
            int cookTimeMin,
            String cuisine,
            List<String> dietaryFlags,
            List<RecipeIngredient> ingredients,
            List<RecipeStep> steps,
            int kcalPerServing
    ) {}

    /** Lightweight search hit (used when listing many candidates). */
    public record RecipeHit(String name, String cuisine, int kcalPerServing, List<String> dietaryFlags) {}

    @AgentTool(description = """
            Generates a complete recipe matching the user's query, dietary flags
            (e.g. "vegan", "gluten-free", "low-carb") and cuisine preference.
            Use when the user asks for a specific recipe, a dish to cook, or
            asks "what should I cook tonight" with constraints.
            Do NOT use for ingredient substitution — use findSubstitute instead.
            """)
    @CacheableToolResult(ttlSeconds = 1800, keyParams = {"query", "dietary"}, scope = CacheScope.USER)
    public Recipe generateRecipe(String query, String dietary, String cuisine) {
        String q = query == null ? "" : query.toLowerCase();
        String d = dietary == null ? "" : dietary.toLowerCase();
        String c = cuisine == null || cuisine.isBlank() ? "italian" : cuisine.toLowerCase();

        if (q.contains("pasta") || q.contains("spaghetti") || q.contains("italian")) {
            return new Recipe(
                    "Spaghetti aglio, olio e peperoncino",
                    2, 5, 12,
                    "italian",
                    List.of("vegetarian", "vegan"),
                    List.of(
                            new RecipeIngredient("spaghetti", 200, "g"),
                            new RecipeIngredient("garlic clove", 4, "pc"),
                            new RecipeIngredient("extra-virgin olive oil", 60, "ml"),
                            new RecipeIngredient("dried chilli flakes", 1, "tsp"),
                            new RecipeIngredient("flat-leaf parsley", 1, "tbsp"),
                            new RecipeIngredient("salt", 1, "tsp")
                    ),
                    List.of(
                            new RecipeStep(1, "Bring a large pot of salted water to the boil and cook spaghetti until al dente", 9),
                            new RecipeStep(2, "While pasta cooks, slice garlic thinly and warm in olive oil with chilli over low heat", 4),
                            new RecipeStep(3, "Reserve 100 ml pasta water, drain spaghetti and toss it in the garlic oil", 1),
                            new RecipeStep(4, "Add reserved water a splash at a time to emulsify the sauce; finish with chopped parsley", 1)
                    ),
                    520
            );
        }

        if (d.contains("vegan") || q.contains("buddha bowl") || q.contains("bowl")) {
            return new Recipe(
                    "Roasted veg & quinoa Buddha bowl",
                    2, 10, 25,
                    c,
                    List.of("vegan", "gluten-free", "dairy-free"),
                    List.of(
                            new RecipeIngredient("quinoa", 150, "g"),
                            new RecipeIngredient("sweet potato", 1, "pc"),
                            new RecipeIngredient("chickpeas (cooked)", 200, "g"),
                            new RecipeIngredient("baby spinach", 80, "g"),
                            new RecipeIngredient("tahini", 2, "tbsp"),
                            new RecipeIngredient("lemon", 1, "pc"),
                            new RecipeIngredient("olive oil", 2, "tbsp")
                    ),
                    List.of(
                            new RecipeStep(1, "Cube sweet potato, toss with oil and salt, roast at 200 °C for 22 minutes", 22),
                            new RecipeStep(2, "Rinse and cook quinoa in 300 ml water for 12 minutes; rest 5 minutes off heat", 17),
                            new RecipeStep(3, "Whisk tahini with lemon juice and a splash of water until pourable", 1),
                            new RecipeStep(4, "Assemble bowls with quinoa, roasted potato, chickpeas, spinach; drizzle the dressing", 2)
                    ),
                    480
            );
        }

        // Default fallback — light and broadly compatible
        return new Recipe(
                "One-pan herby chicken & vegetables",
                2, 10, 25,
                c,
                List.of("gluten-free", "dairy-free"),
                List.of(
                        new RecipeIngredient("chicken thigh (boneless)", 300, "g"),
                        new RecipeIngredient("courgette", 1, "pc"),
                        new RecipeIngredient("cherry tomatoes", 200, "g"),
                        new RecipeIngredient("red onion", 1, "pc"),
                        new RecipeIngredient("olive oil", 2, "tbsp"),
                        new RecipeIngredient("dried oregano", 1, "tsp"),
                        new RecipeIngredient("lemon", 1, "pc")
                ),
                List.of(
                        new RecipeStep(1, "Cube chicken and slice vegetables into similar-sized pieces", 4),
                        new RecipeStep(2, "Combine on a tray with oil, oregano, salt and pepper", 2),
                        new RecipeStep(3, "Roast at 210 °C for 22 minutes, tossing once halfway through", 22),
                        new RecipeStep(4, "Squeeze lemon over the tray before serving", 1)
                ),
                430
        );
    }

    @AgentTool(description = """
            Searches the recipe catalogue for matches by keyword and dietary flag.
            Returns a list of lightweight hits — use generateRecipe to get full
            instructions for one of them. Independent from generateRecipe;
            can be called in parallel.
            """, parallelizable = true)
    @ToolRetry(maxAttempts = 3, waitDurationMs = 250, retryOn = {IOException.class})
    @CacheableToolResult(ttlSeconds = 3600, keyParams = {"query", "dietary"}, scope = CacheScope.GLOBAL)
    public List<RecipeHit> searchRecipes(String query, String dietary) {
        String q = query == null ? "" : query.toLowerCase();
        String d = dietary == null ? "" : dietary.toLowerCase();

        List<RecipeHit> hits = List.of(
                new RecipeHit("Spaghetti aglio, olio e peperoncino", "italian", 520, List.of("vegetarian", "vegan")),
                new RecipeHit("Margherita pizza", "italian", 700, List.of("vegetarian")),
                new RecipeHit("Roasted veg & quinoa Buddha bowl", "fusion", 480, List.of("vegan", "gluten-free")),
                new RecipeHit("Chicken tikka masala", "indian", 650, List.of()),
                new RecipeHit("Chana masala", "indian", 420, List.of("vegan", "gluten-free")),
                new RecipeHit("Beef pho", "vietnamese", 540, List.of("dairy-free")),
                new RecipeHit("Tofu stir-fry with broccoli", "chinese", 380, List.of("vegan", "gluten-free")),
                new RecipeHit("Greek salad with feta", "greek", 290, List.of("vegetarian", "gluten-free"))
        );

        return hits.stream()
                .filter(h -> q.isBlank()
                        || h.name().toLowerCase().contains(q)
                        || h.cuisine().toLowerCase().contains(q))
                .filter(h -> d.isBlank() || h.dietaryFlags().contains(d))
                .toList();
    }
}
