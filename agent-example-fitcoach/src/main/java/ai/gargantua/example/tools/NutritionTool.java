package ai.gargantua.example.tools;

import ai.gargantua.core.tool.AgentTool;
import ai.gargantua.core.tool.CacheScope;
import ai.gargantua.core.tool.CacheableToolResult;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tools for nutrition advice, meal planning, and food lookup.
 * Demonstrates {@code @CacheableToolResult} with GLOBAL scope and
 * structured record return types for LLM consumption.
 */
@Component
public class NutritionTool {

    /** A single meal with macronutrient breakdown. */
    public record Meal(String name, int calories, int proteinGrams, int carbsGrams, int fatGrams) {}

    /** A complete daily meal plan with calorie target and notes. */
    public record MealPlan(String planName, String goal, List<Meal> meals, int totalCalories, String notes) {}

    /** Nutritional information for a specific food item. */
    public record NutrientInfo(String food, int caloriesPer100g, int protein, int carbs, int fat, List<String> vitamins) {}

    @AgentTool(description = """
            Creates a daily meal plan based on the user's caloric needs and dietary goal.
            Use when the user asks for diet plans, meal suggestions, or nutrition programs.
            """)
    public MealPlan createMealPlan(String goal, int targetCalories) {
        return switch (goal.toLowerCase()) {
            case "muscle_gain", "muscle gain", "bulking" -> new MealPlan(
                    "High-Protein Muscle Builder",
                    goal,
                    List.of(
                            new Meal("Oatmeal with Whey Protein & Banana", 520, 35, 65, 12),
                            new Meal("Grilled Chicken Breast with Brown Rice & Broccoli", 650, 50, 60, 15),
                            new Meal("Greek Yogurt with Almonds & Honey", 320, 20, 30, 14),
                            new Meal("Salmon with Sweet Potato & Asparagus", 580, 40, 45, 20),
                            new Meal("Cottage Cheese with Berries", 230, 25, 15, 5)
                    ),
                    2300,
                    "Aim for 1.6-2.2g protein per kg body weight. Adjust portions to hit " + targetCalories + " kcal target."
            );
            case "fat_loss", "fat loss", "cutting", "weight loss" -> new MealPlan(
                    "Lean & Clean Fat Loss",
                    goal,
                    List.of(
                            new Meal("Egg White Omelette with Spinach & Tomato", 280, 28, 10, 8),
                            new Meal("Turkey Lettuce Wraps with Avocado", 350, 30, 12, 18),
                            new Meal("Protein Shake with Almond Milk", 200, 30, 8, 5),
                            new Meal("Grilled Cod with Roasted Vegetables", 380, 35, 25, 10),
                            new Meal("Celery Sticks with Almond Butter", 150, 5, 6, 12)
                    ),
                    1360,
                    "Maintain a 500 kcal deficit. Prioritize protein to preserve muscle. Target: " + targetCalories + " kcal."
            );
            default -> new MealPlan(
                    "Balanced Maintenance",
                    goal,
                    List.of(
                            new Meal("Whole Grain Toast with Avocado & Eggs", 450, 20, 35, 25),
                            new Meal("Quinoa Bowl with Chickpeas & Vegetables", 520, 22, 65, 16),
                            new Meal("Apple with Peanut Butter", 250, 7, 30, 14),
                            new Meal("Grilled Chicken Salad with Olive Oil Dressing", 480, 38, 20, 22),
                            new Meal("Mixed Nuts & Dark Chocolate", 300, 8, 20, 22)
                    ),
                    2000,
                    "Balanced macros: ~30% protein, ~40% carbs, ~30% fat. Target: " + targetCalories + " kcal."
            );
        };
    }

    @AgentTool(description = """
            Looks up nutritional information for a specific food item.
            Use when the user asks "how many calories in X" or "is X healthy".
            """)
    @CacheableToolResult(ttlSeconds = 3600, keyParams = {"food"}, scope = CacheScope.GLOBAL)
    public NutrientInfo lookupFood(String food) {
        return switch (food.toLowerCase()) {
            case "chicken breast" -> new NutrientInfo("Chicken Breast", 165, 31, 0, 4,
                    List.of("B6", "B12", "Niacin", "Phosphorus", "Selenium"));
            case "salmon" -> new NutrientInfo("Salmon", 208, 20, 0, 13,
                    List.of("D", "B12", "Omega-3", "Selenium", "B6"));
            case "brown rice" -> new NutrientInfo("Brown Rice", 112, 2, 24, 1,
                    List.of("B1", "B3", "B6", "Manganese", "Magnesium"));
            case "banana" -> new NutrientInfo("Banana", 89, 1, 23, 0,
                    List.of("B6", "C", "Potassium", "Manganese", "Fiber"));
            case "avocado" -> new NutrientInfo("Avocado", 160, 2, 9, 15,
                    List.of("K", "C", "B5", "B6", "E", "Potassium", "Folate"));
            case "egg", "eggs" -> new NutrientInfo("Egg (whole)", 155, 13, 1, 11,
                    List.of("B12", "D", "B2", "Selenium", "Choline"));
            case "broccoli" -> new NutrientInfo("Broccoli", 34, 3, 7, 0,
                    List.of("C", "K", "A", "Folate", "Fiber", "Potassium"));
            case "sweet potato" -> new NutrientInfo("Sweet Potato", 86, 2, 20, 0,
                    List.of("A", "C", "B6", "Potassium", "Manganese", "Fiber"));
            default -> new NutrientInfo(food, 100, 5, 15, 3,
                    List.of("Varies"));
        };
    }
}
