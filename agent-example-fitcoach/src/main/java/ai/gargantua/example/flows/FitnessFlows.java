package ai.gargantua.example.flows;

import ai.gargantua.core.flow.AgentsFlow;
import ai.gargantua.core.flow.FlowDefinition;
import org.springframework.stereotype.Component;

/**
 * Multi-step agent flows for the FitCoach AI example.
 * Each flow chains multiple skills in sequence, with each step's output
 * becoming context for the next step.
 */
@Component
public class FitnessFlows {

    /**
     * Complete fitness plan: health assessment → workout → nutrition.
     * The agent first assesses the user's health, then creates a workout plan
     * tailored to the assessment, then a nutrition plan matching the workout.
     */
    @AgentsFlow(name = "full-fitness-plan", description = "Creates a complete fitness plan: health assessment, workout, then nutrition")
    public void fullFitnessPlan(FlowDefinition flow) {
        flow.step("health-skill", "Assess the user's current health and fitness level based on their message")
            .step("workout-skill", "Based on the health assessment above, create a personalized workout plan")
            .step("nutrition-skill", "Based on the workout plan above, create a matching nutrition and meal plan");
    }

    /**
     * Research and plan: fetch latest fitness news → create informed workout.
     * The agent first gathers current fitness trends, then uses them to create
     * a modern, evidence-based workout plan.
     */
    @AgentsFlow(name = "research-workout", description = "Research latest fitness trends then create an informed workout plan")
    public void researchWorkout(FlowDefinition flow) {
        flow.step("news-skill", "Find the latest research and trends in fitness training")
            .step("workout-skill", "Using the latest research above, create a modern evidence-based workout plan");
    }

    /**
     * Iterative workout refinement: create a plan, then review and improve it
     * up to 3 times. The reviewer skill exits early if it signals [DONE] or [SATISFIED].
     */
    @AgentsFlow(name = "iterative-workout", description = "Creates and refines a workout plan iteratively")
    public void iterativeWorkout(FlowDefinition flow) {
        flow.step("workout-skill", "Create an initial workout plan")
            .loop("reviewer-skill", 3);  // Review and improve up to 3 times
    }

    /**
     * Parallel assessment: run health assessment and nutrition lookup simultaneously,
     * then create a combined workout plan from both results.
     */
    @AgentsFlow(name = "parallel-assessment", description = "Parallel health + nutrition assessment, then combined workout")
    public void parallelAssessment(FlowDefinition flow) {
        flow.parallel("health-skill", "nutrition-skill")
            .step("workout-skill", "Create a workout plan based on the health assessment and nutrition info above");
    }
}
