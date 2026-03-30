package ai.gargantua.example;

import ai.gargantua.example.tools.HealthTool;
import ai.gargantua.example.tools.NewsTool;
import ai.gargantua.example.tools.NutritionTool;
import ai.gargantua.example.tools.ProfileTool;
import ai.gargantua.example.tools.WorkoutTool;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.stereotype.Component;

/**
 * GraalVM native-image runtime hints for the FitCoach AI agent. Registers tool
 * classes and their inner record types for reflection, since native-image cannot
 * discover them automatically. Also registers resource patterns for skill files.
 */
@Component
@ImportRuntimeHints(AgentKitRuntimeHints.Registrar.class)
public class AgentKitRuntimeHints {

    static class Registrar implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            // WorkoutTool records
            registerToolClass(hints, WorkoutTool.class);
            registerToolClass(hints, WorkoutTool.Exercise.class);
            registerToolClass(hints, WorkoutTool.WorkoutPlan.class);

            // NutritionTool records
            registerToolClass(hints, NutritionTool.class);
            registerToolClass(hints, NutritionTool.Meal.class);
            registerToolClass(hints, NutritionTool.MealPlan.class);
            registerToolClass(hints, NutritionTool.NutrientInfo.class);

            // HealthTool records
            registerToolClass(hints, HealthTool.class);
            registerToolClass(hints, HealthTool.HealthMetric.class);
            registerToolClass(hints, HealthTool.BmiResult.class);

            // NewsTool records
            registerToolClass(hints, NewsTool.class);
            registerToolClass(hints, NewsTool.NewsArticle.class);

            // ProfileTool records
            registerToolClass(hints, ProfileTool.class);
            registerToolClass(hints, ProfileTool.UserProfile.class);

            // Register resource patterns for skills and static assets
            hints.resources().registerPattern("skills/**");
            hints.resources().registerPattern("static/**");
        }

        private void registerToolClass(RuntimeHints hints, Class<?> clazz) {
            hints.reflection().registerType(clazz,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.INVOKE_PUBLIC_METHODS,
                    MemberCategory.DECLARED_FIELDS,
                    MemberCategory.PUBLIC_FIELDS);
        }
    }
}
