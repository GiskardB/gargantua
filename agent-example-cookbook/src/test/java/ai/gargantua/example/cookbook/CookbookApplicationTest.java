package ai.gargantua.example.cookbook;

import ai.gargantua.example.cookbook.enrichers.DietaryProfileEnricher;
import ai.gargantua.example.cookbook.flows.CookbookFlows;
import ai.gargantua.example.cookbook.tools.AdminTool;
import ai.gargantua.example.cookbook.tools.IngredientTool;
import ai.gargantua.example.cookbook.tools.PantryTool;
import ai.gargantua.example.cookbook.tools.RecipeTool;
import ai.gargantua.example.cookbook.tools.TimerTool;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke + unit-level tests for the cookbook example. Boots the full Spring
 * context using the {@code embedded} profile so MongoDB and Redis are replaced
 * with in-memory adapters by {@code EmbeddedProfileAutoConfiguration}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("embedded")
class CookbookApplicationTest {

    @Autowired
    private ApplicationContext ctx;

    @Test
    void contextLoads() {
        assertNotNull(ctx);
    }

    @Test
    void allToolBeansAreRegistered() {
        assertNotNull(ctx.getBean(RecipeTool.class));
        assertNotNull(ctx.getBean(IngredientTool.class));
        assertNotNull(ctx.getBean(PantryTool.class));
        assertNotNull(ctx.getBean(TimerTool.class));
        assertNotNull(ctx.getBean(AdminTool.class));
    }

    @Test
    void dietaryEnricherIsRegistered() {
        assertNotNull(ctx.getBean(DietaryProfileEnricher.class));
    }

    @Test
    void flowsAreRegistered() {
        assertNotNull(ctx.getBean(CookbookFlows.class));
    }

    @Test
    void recipeToolGeneratesPasta() {
        var tool = ctx.getBean(RecipeTool.class);
        var recipe = tool.generateRecipe("spaghetti aglio e olio", "vegan", "italian");
        assertNotNull(recipe);
        assertEquals("Spaghetti aglio, olio e peperoncino", recipe.name());
        assertFalse(recipe.ingredients().isEmpty());
        assertFalse(recipe.steps().isEmpty());
        assertTrue(recipe.dietaryFlags().contains("vegan"));
    }

    @Test
    void recipeSearchFiltersByDietary() {
        var tool = ctx.getBean(RecipeTool.class);
        var hits = tool.searchRecipes("", "vegan");
        assertNotNull(hits);
        assertFalse(hits.isEmpty());
        assertTrue(hits.stream().allMatch(h -> h.dietaryFlags().contains("vegan")));
    }

    @Test
    void ingredientNutritionLookup() {
        var tool = ctx.getBean(IngredientTool.class);
        var n = tool.getNutrition("chicken breast");
        assertNotNull(n);
        assertEquals(165, n.kcal());
        assertEquals(31.0, n.proteinG(), 0.01);
    }

    @Test
    void ingredientSubstituteForVegan() {
        var tool = ctx.getBean(IngredientTool.class);
        var sub = tool.findSubstitute("milk", "vegan");
        assertNotNull(sub);
        assertEquals("oat milk", sub.replacement());
        assertEquals(1.0, sub.ratio(), 0.001);
    }

    @Test
    void pantryRoundTrip() {
        var tool = ctx.getBean(PantryTool.class);
        assertTrue(tool.getPantry("user-001").isEmpty());

        var added = tool.addToPantry("user-001", "olive oil", 500, "ml");
        assertEquals("olive oil", added.ingredient());
        assertEquals(1, tool.getPantry("user-001").size());

        boolean removed = tool.removeFromPantry("user-001", "olive oil");
        assertTrue(removed);
        assertTrue(tool.getPantry("user-001").isEmpty());
    }

    @Test
    void timerHandlesValidInput() {
        var tool = ctx.getBean(TimerTool.class);
        var t = tool.setTimer("pasta", 9);
        assertNotNull(t);
        assertEquals("pasta", t.name());
        assertEquals(9, t.minutes());
        assertNotNull(t.firesAt());
    }

    @Test
    void timerClampsExtremeValues() {
        var tool = ctx.getBean(TimerTool.class);
        assertEquals(1, tool.setTimer("zero", 0).minutes());
        assertEquals(24 * 60, tool.setTimer("huge", 999_999).minutes());
    }

    @Test
    void adminAuditLogReturnsEntries() {
        var tool = ctx.getBean(AdminTool.class);
        var log = tool.viewAuditLog("user-001");
        assertNotNull(log);
        assertFalse(log.isEmpty());
        assertEquals("user-001", log.get(0).userId());
    }
}
