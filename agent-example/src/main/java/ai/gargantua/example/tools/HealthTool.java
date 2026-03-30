package ai.gargantua.example.tools;

import ai.gargantua.core.tool.AgentTool;
import ai.gargantua.core.tool.RequiresApproval;
import org.springframework.stereotype.Component;

/**
 * Tools for health metrics, BMI calculation, and wellness tracking.
 * Demonstrates {@code @RequiresApproval} for human-in-the-loop confirmation
 * before writing data that modifies a user's health profile.
 */
@Component
public class HealthTool {

    /** A recorded health metric with status assessment. */
    public record HealthMetric(String metric, double value, String unit, String status, String recommendation) {}

    /** BMI calculation result with category and guidance. */
    public record BmiResult(double bmi, String category, String recommendation) {}

    @AgentTool(description = """
            Calculates BMI and provides a health assessment based on weight and height.
            Use when the user provides their weight and height or asks about BMI.
            """)
    public BmiResult calculateBmi(double weightKg, double heightCm) {
        double bmi = weightKg / Math.pow(heightCm / 100.0, 2);
        bmi = Math.round(bmi * 10) / 10.0;

        String category;
        String recommendation;
        if (bmi < 18.5) {
            category = "underweight";
            recommendation = "Consider increasing caloric intake with nutrient-dense foods. Consult a healthcare provider.";
        } else if (bmi < 25) {
            category = "normal";
            recommendation = "Great job! Maintain your current healthy lifestyle with balanced nutrition and regular exercise.";
        } else if (bmi < 30) {
            category = "overweight";
            recommendation = "Consider a moderate caloric deficit and increasing physical activity. Consult a healthcare provider.";
        } else {
            category = "obese";
            recommendation = "Please consult a healthcare provider for a personalized weight management plan.";
        }

        return new BmiResult(bmi, category, recommendation);
    }

    @AgentTool(description = """
            Records a health metric for the user (weight, body fat %, resting heart rate, etc.).
            This modifies the user's health profile — requires approval.
            """)
    @RequiresApproval(
            message = "The agent wants to update your health profile with a new measurement.",
            showParameters = {"metric", "value"},
            dangerous = false
    )
    public HealthMetric recordMetric(String metric, double value, String unit) {
        String status;
        String recommendation;

        switch (metric.toLowerCase()) {
            case "weight" -> {
                status = "recorded";
                recommendation = "Weight tracked. Consistent monitoring helps identify trends over time.";
            }
            case "body fat" -> {
                status = value < 15 ? "athletic" : value < 25 ? "healthy" : "above average";
                recommendation = "Body fat recorded. Combine with weight trends for a complete picture.";
            }
            case "resting heart rate" -> {
                status = value < 60 ? "excellent" : value < 80 ? "normal" : "elevated";
                recommendation = value >= 80
                        ? "Elevated resting heart rate. Consider cardiovascular training and consult a doctor."
                        : "Healthy resting heart rate. Keep up your cardio routine.";
            }
            case "blood pressure systolic" -> {
                status = value < 120 ? "normal" : value < 140 ? "elevated" : "high";
                recommendation = value >= 140
                        ? "High blood pressure detected. Please consult your healthcare provider."
                        : "Blood pressure within normal range.";
            }
            default -> {
                status = "recorded";
                recommendation = "Metric recorded successfully. Track regularly for meaningful trends.";
            }
        }

        return new HealthMetric(metric, value, unit, status, recommendation);
    }
}
