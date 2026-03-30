package ai.gargantua.example.tools;

import ai.gargantua.core.tool.AgentTool;
import ai.gargantua.core.tool.CacheScope;
import ai.gargantua.core.tool.CacheableToolResult;
import ai.gargantua.core.tool.ToolRetry;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Tools for generating personalized workout plans and searching exercises.
 * Demonstrates {@code @CacheableToolResult} with USER scope, {@code @ToolRetry},
 * and {@code parallelizable} tool calls.
 */
@Component
public class WorkoutTool {

    /** A single exercise within a workout plan. */
    public record Exercise(String name, int sets, int reps, String muscleGroup, String difficulty) {}

    /** A complete workout plan with exercises, duration estimate, and notes. */
    public record WorkoutPlan(String planName, String goal, List<Exercise> exercises, int estimatedMinutes, String notes) {}

    @AgentTool(description = """
            Generates a personalized workout plan based on the user's goal, fitness level, and available equipment.
            Use when the user asks for a workout plan, exercise routine, or training program.
            Do NOT use for nutrition or diet questions.
            """)
    @CacheableToolResult(ttlSeconds = 600, keyParams = {"goal", "level"}, scope = CacheScope.USER)
    public WorkoutPlan generateWorkout(String goal, String level, String equipment) {
        return switch (goal.toLowerCase()) {
            case "muscle_gain", "muscle gain", "hypertrophy" -> new WorkoutPlan(
                    "Hypertrophy Builder",
                    goal,
                    List.of(
                            new Exercise("Barbell Bench Press", 4, 8, "chest", level),
                            new Exercise("Barbell Squat", 4, 8, "quadriceps", level),
                            new Exercise("Bent-Over Row", 4, 8, "back", level),
                            new Exercise("Overhead Press", 3, 10, "shoulders", level),
                            new Exercise("Romanian Deadlift", 3, 10, "hamstrings", level),
                            new Exercise("Bicep Curls", 3, 12, "biceps", level),
                            new Exercise("Tricep Dips", 3, 12, "triceps", level)
                    ),
                    55,
                    "Rest 90s between sets. Progressive overload weekly. Equipment: " + equipment
            );
            case "fat_loss", "fat loss", "weight loss" -> new WorkoutPlan(
                    "Fat Burner Circuit",
                    goal,
                    List.of(
                            new Exercise("Burpees", 3, 15, "full body", level),
                            new Exercise("Mountain Climbers", 3, 20, "core", level),
                            new Exercise("Kettlebell Swings", 3, 15, "posterior chain", level),
                            new Exercise("Jump Squats", 3, 12, "legs", level),
                            new Exercise("Push-Ups", 3, 15, "chest", level),
                            new Exercise("Plank Hold", 3, 1, "core", level)
                    ),
                    40,
                    "Minimal rest (30s) between exercises. Circuit style. Equipment: " + equipment
            );
            case "endurance" -> new WorkoutPlan(
                    "Endurance Builder",
                    goal,
                    List.of(
                            new Exercise("Running Intervals", 5, 1, "cardiovascular", level),
                            new Exercise("Cycling", 3, 1, "legs", level),
                            new Exercise("Rowing Machine", 3, 1, "full body", level),
                            new Exercise("Box Jumps", 3, 15, "legs", level),
                            new Exercise("Battle Ropes", 3, 1, "upper body", level)
                    ),
                    50,
                    "Focus on sustained effort. Track heart rate zones. Equipment: " + equipment
            );
            default -> new WorkoutPlan(
                    "Flexibility & Mobility",
                    goal,
                    List.of(
                            new Exercise("Sun Salutation Flow", 3, 5, "full body", level),
                            new Exercise("Hip Flexor Stretch", 3, 1, "hips", level),
                            new Exercise("Shoulder Dislocates", 3, 10, "shoulders", level),
                            new Exercise("Pigeon Pose", 2, 1, "hips", level),
                            new Exercise("Cat-Cow Stretch", 3, 10, "spine", level),
                            new Exercise("Hamstring Stretch", 3, 1, "hamstrings", level)
                    ),
                    35,
                    "Hold each stretch 30-60 seconds. Breathe deeply. Equipment: " + equipment
            );
        };
    }

    @AgentTool(description = """
            Searches the exercise database for exercises targeting a specific muscle group.
            Independent from generateWorkout -- can be called in parallel.
            """, parallelizable = true)
    @ToolRetry(maxAttempts = 3, waitDurationMs = 300, retryOn = {IOException.class})
    @CacheableToolResult(ttlSeconds = 300, keyParams = {"muscleGroup"}, scope = CacheScope.GLOBAL)
    public List<Exercise> searchExercises(String muscleGroup) {
        return switch (muscleGroup.toLowerCase()) {
            case "chest" -> List.of(
                    new Exercise("Barbell Bench Press", 4, 8, "chest", "intermediate"),
                    new Exercise("Incline Dumbbell Press", 3, 10, "chest", "intermediate"),
                    new Exercise("Cable Crossover", 3, 12, "chest", "beginner"),
                    new Exercise("Push-Ups", 3, 15, "chest", "beginner"),
                    new Exercise("Dumbbell Fly", 3, 12, "chest", "intermediate"),
                    new Exercise("Decline Bench Press", 3, 10, "chest", "advanced")
            );
            case "back" -> List.of(
                    new Exercise("Pull-Ups", 4, 8, "back", "intermediate"),
                    new Exercise("Bent-Over Row", 4, 8, "back", "intermediate"),
                    new Exercise("Lat Pulldown", 3, 10, "back", "beginner"),
                    new Exercise("Seated Cable Row", 3, 10, "back", "beginner"),
                    new Exercise("T-Bar Row", 3, 8, "back", "advanced"),
                    new Exercise("Face Pulls", 3, 15, "back", "beginner")
            );
            case "legs", "quadriceps", "hamstrings" -> List.of(
                    new Exercise("Barbell Squat", 4, 8, "quadriceps", "intermediate"),
                    new Exercise("Leg Press", 3, 10, "quadriceps", "beginner"),
                    new Exercise("Romanian Deadlift", 3, 10, "hamstrings", "intermediate"),
                    new Exercise("Leg Curl", 3, 12, "hamstrings", "beginner"),
                    new Exercise("Walking Lunges", 3, 12, "legs", "intermediate"),
                    new Exercise("Calf Raises", 4, 15, "calves", "beginner"),
                    new Exercise("Bulgarian Split Squat", 3, 10, "quadriceps", "advanced")
            );
            case "shoulders" -> List.of(
                    new Exercise("Overhead Press", 4, 8, "shoulders", "intermediate"),
                    new Exercise("Lateral Raises", 3, 12, "shoulders", "beginner"),
                    new Exercise("Front Raises", 3, 12, "shoulders", "beginner"),
                    new Exercise("Arnold Press", 3, 10, "shoulders", "intermediate"),
                    new Exercise("Reverse Fly", 3, 12, "shoulders", "intermediate")
            );
            default -> List.of(
                    new Exercise("Plank", 3, 1, "core", "beginner"),
                    new Exercise("Russian Twist", 3, 15, "core", "intermediate"),
                    new Exercise("Dead Bug", 3, 10, "core", "beginner"),
                    new Exercise("Hanging Leg Raise", 3, 10, "core", "advanced"),
                    new Exercise("Ab Wheel Rollout", 3, 8, "core", "advanced")
            );
        };
    }
}
