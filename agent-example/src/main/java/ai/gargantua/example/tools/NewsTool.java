package ai.gargantua.example.tools;

import ai.gargantua.core.tool.AgentTool;
import ai.gargantua.core.tool.CacheScope;
import ai.gargantua.core.tool.CacheableToolResult;
import ai.gargantua.core.tool.ToolRetry;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * Tool for fetching fitness, sports, and health news.
 * Demonstrates {@code @ToolRetry} for flaky external API calls
 * and {@code @CacheableToolResult} with GLOBAL scope to avoid redundant fetches.
 */
@Component
public class NewsTool {

    /** A news article summary with source attribution. */
    public record NewsArticle(String title, String source, String summary, String url, String publishedAt) {}

    @AgentTool(description = """
            Fetches latest fitness, sports, and health news articles.
            Use when the user asks about recent sports news, fitness trends, or health studies.
            """)
    @ToolRetry(maxAttempts = 2, waitDurationMs = 500, retryOn = {IOException.class})
    @CacheableToolResult(ttlSeconds = 900, keyParams = {"topic"}, scope = CacheScope.GLOBAL)
    public List<NewsArticle> fetchNews(String topic) {
        String today = LocalDate.now().toString();
        String yesterday = LocalDate.now().minusDays(1).toString();

        return switch (topic.toLowerCase()) {
            case "fitness", "workout", "exercise" -> List.of(
                    new NewsArticle(
                            "New Study: Short Intense Workouts as Effective as Longer Sessions",
                            "Journal of Sports Medicine",
                            "Researchers found that 20-minute high-intensity sessions produced similar muscle adaptation to 60-minute moderate sessions over 12 weeks.",
                            "https://example.com/news/short-workouts-study",
                            today),
                    new NewsArticle(
                            "Wearable Fitness Tech Market Surges 35% in 2026",
                            "TechFit Daily",
                            "Smart rings and AI-powered fitness trackers are driving unprecedented growth in the wearable fitness technology market.",
                            "https://example.com/news/wearable-market",
                            today),
                    new NewsArticle(
                            "Functional Training Overtakes CrossFit as Top Fitness Trend",
                            "Health & Fitness Magazine",
                            "Industry survey reveals functional movement training is now the most popular group fitness format worldwide.",
                            "https://example.com/news/functional-training-trend",
                            yesterday)
            );
            case "nutrition", "diet", "food" -> List.of(
                    new NewsArticle(
                            "Mediterranean Diet Linked to 30% Lower Dementia Risk",
                            "Nature Nutrition",
                            "A 15-year longitudinal study shows strong correlation between Mediterranean dietary patterns and reduced cognitive decline.",
                            "https://example.com/news/mediterranean-dementia",
                            today),
                    new NewsArticle(
                            "FDA Approves New Protein Quality Rating System",
                            "Food Science Weekly",
                            "The updated DIAAS-based labeling will help consumers compare protein quality across plant and animal sources.",
                            "https://example.com/news/protein-rating",
                            yesterday),
                    new NewsArticle(
                            "Lab-Grown Meat Reaches Price Parity with Conventional Beef",
                            "FutureFoods",
                            "Cultivated meat companies announce retail prices matching ground beef, signaling a potential shift in the protein market.",
                            "https://example.com/news/lab-meat-parity",
                            yesterday)
            );
            case "sports", "athletics", "competition" -> List.of(
                    new NewsArticle(
                            "Olympic Committee Announces AI-Assisted Coaching Rules for 2028",
                            "Sports Illustrated",
                            "New guidelines establish boundaries for AI coaching tools during Olympic training and competition.",
                            "https://example.com/news/olympics-ai-coaching",
                            today),
                    new NewsArticle(
                            "Marathon World Record Broken with Sub-1:58 Time",
                            "Runner's World",
                            "Elite runner shatters the two-hour marathon barrier in controlled conditions with advanced carbon-plate shoes.",
                            "https://example.com/news/marathon-record",
                            yesterday),
                    new NewsArticle(
                            "esports Athletes Adopt Traditional Fitness Regimens",
                            "GameFit",
                            "Top esports teams now require players to complete daily physical fitness programs to improve reaction time and endurance.",
                            "https://example.com/news/esports-fitness",
                            yesterday)
            );
            default -> List.of(
                    new NewsArticle(
                            "WHO Releases Updated Physical Activity Guidelines",
                            "World Health Organization",
                            "New guidelines recommend 150-300 minutes of moderate activity per week, with added emphasis on strength training twice weekly.",
                            "https://example.com/news/who-activity-guidelines",
                            today),
                    new NewsArticle(
                            "Sleep Quality Identified as Top Factor in Athletic Recovery",
                            "Sleep Research Journal",
                            "Meta-analysis of 50 studies confirms sleep quality outweighs nutrition timing for post-exercise recovery.",
                            "https://example.com/news/sleep-recovery",
                            today),
                    new NewsArticle(
                            "Mental Health Benefits of Exercise Quantified in Landmark Study",
                            "The Lancet Psychiatry",
                            "Exercise reduces mental health burden days by 43%, with team sports showing the greatest benefit.",
                            "https://example.com/news/mental-health-exercise",
                            yesterday),
                    new NewsArticle(
                            "Cold Plunge Therapy: Separating Hype from Science",
                            "Scientific American",
                            "Controlled trials show modest benefits for inflammation but limited evidence for fat loss claims.",
                            "https://example.com/news/cold-plunge-science",
                            yesterday)
            );
        };
    }
}
