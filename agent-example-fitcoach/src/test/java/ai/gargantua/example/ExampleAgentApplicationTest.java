package ai.gargantua.example;

import ai.gargantua.example.enrichers.FitnessProfileEnricher;
import ai.gargantua.example.tools.HealthTool;
import ai.gargantua.example.tools.NewsTool;
import ai.gargantua.example.tools.NutritionTool;
import ai.gargantua.example.tools.ProfileTool;
import ai.gargantua.example.tools.WorkoutTool;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.data.mongodb.uri=",
                "spring.data.mongodb.host=localhost",
                "spring.data.mongodb.port=0",
                "spring.autoconfigure.exclude=" +
                        "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration"
        }
)
@ActiveProfiles("test")
class ExampleAgentApplicationTest {

    @Autowired
    private ApplicationContext ctx;

    @Test
    void contextLoads() {
        // Verifies that the Spring application context starts successfully
        assertNotNull(ctx);
    }

    @Test
    void allToolBeansAreRegistered() {
        assertNotNull(ctx.getBean(WorkoutTool.class));
        assertNotNull(ctx.getBean(NutritionTool.class));
        assertNotNull(ctx.getBean(HealthTool.class));
        assertNotNull(ctx.getBean(NewsTool.class));
        assertNotNull(ctx.getBean(ProfileTool.class));
    }

    @Test
    void enricherBeanIsRegistered() {
        assertNotNull(ctx.getBean(FitnessProfileEnricher.class));
    }

    @Test
    void workoutToolGeneratesPlans() {
        var tool = ctx.getBean(WorkoutTool.class);
        var plan = tool.generateWorkout("muscle_gain", "intermediate", "barbell");
        assertNotNull(plan);
        assertEquals("Hypertrophy Builder", plan.planName());
        assertFalse(plan.exercises().isEmpty());
    }

    @Test
    void nutritionToolCreatesMealPlan() {
        var tool = ctx.getBean(NutritionTool.class);
        var plan = tool.createMealPlan("fat_loss", 1500);
        assertNotNull(plan);
        assertEquals("Lean & Clean Fat Loss", plan.planName());
        assertFalse(plan.meals().isEmpty());
    }

    @Test
    void healthToolCalculatesBmi() {
        var tool = ctx.getBean(HealthTool.class);
        var result = tool.calculateBmi(75.0, 180.0);
        assertNotNull(result);
        assertEquals("normal", result.category());
        assertTrue(result.bmi() > 20 && result.bmi() < 25);
    }

    @Test
    void newsToolFetchesArticles() {
        var tool = ctx.getBean(NewsTool.class);
        var articles = tool.fetchNews("fitness");
        assertNotNull(articles);
        assertFalse(articles.isEmpty());
        assertTrue(articles.size() >= 3);
    }

    @Test
    void profileToolReturnsProfile() {
        var tool = ctx.getBean(ProfileTool.class);
        var profile = tool.getProfile("user-001");
        assertNotNull(profile);
        assertEquals("Alex Johnson", profile.name());
        assertEquals("muscle gain", profile.goal());
    }
}
