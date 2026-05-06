package ai.gargantua.example.cookbook.flows;

import ai.gargantua.core.flow.AgentsFlow;
import ai.gargantua.core.flow.FlowDefinition;
import org.springframework.stereotype.Component;

/**
 * Multi-step flows for the cookbook agent. Each {@code @AgentsFlow} is exposed
 * over the {@code /api/flows} REST endpoint and chains skills together — each
 * step's output is fed into the next as additional context.
 */
@Component
public class CookbookFlows {

    /**
     * Plan a meal end-to-end: ingredient research → recipe selection → pantry
     * delta. Useful when the user says "plan dinner for tomorrow night".
     */
    @AgentsFlow(name = "full-meal-plan",
            description = "Researches ingredients, picks a recipe, then drafts the pantry shopping list")
    public void fullMealPlan(FlowDefinition flow) {
        flow.step("ingredient-skill", "Identify and validate the key ingredients for the requested dish based on the user's dietary profile")
            .step("recipe-skill", "Using the validated ingredients above, generate a complete recipe in structured form")
            .step("pantry-skill", "Compare the recipe ingredients with the current pantry and produce a shopping list");
    }

    /**
     * Iterative recipe refinement: produce a candidate, then loop the
     * recipe-skill on itself up to 3 times to improve it. Useful for "make this
     * recipe healthier / quicker / lower-carb".
     */
    @AgentsFlow(name = "iterative-recipe",
            description = "Generates a recipe and iteratively refines it up to 3 times")
    public void iterativeRecipe(FlowDefinition flow) {
        flow.step("recipe-skill", "Generate an initial recipe matching the user's request")
            .loop("recipe-skill", 3);
    }

    /**
     * Parallel scout: search recipes and look up ingredient nutrition at the
     * same time, then compose a single answer that ranks options by both
     * culinary fit and macro profile.
     */
    @AgentsFlow(name = "parallel-recipe-scout",
            description = "Search recipes and look up ingredient nutrition in parallel, then summarise")
    public void parallelRecipeScout(FlowDefinition flow) {
        flow.parallel("recipe-skill", "ingredient-skill")
            .step("recipe-skill", "Combine the recipe candidates and the nutrition data above into a single ranked recommendation");
    }
}
