package ai.gargantua.example.tools;

import ai.gargantua.core.tool.AgentTool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Reference implementation of a web search tool. Returns mock results.
 * Demonstrates a simple {@code @AgentTool} with no additional annotations.
 */
@Component
public class SearchTool {

    public record SearchResultItem(
            String title,
            String url,
            String snippet
    ) {}

    public record SearchResult(
            String query,
            List<SearchResultItem> results,
            int totalResults,
            String timestamp
    ) {}

    @AgentTool(description = "Search the web for information on a given query")
    public SearchResult searchWeb(String query) {
        // Mock implementation returning hardcoded search results
        List<SearchResultItem> items = List.of(
                new SearchResultItem(
                        "Result 1 for: " + query,
                        "https://example.com/result1",
                        "This is a summary of the first search result for " + query + "."
                ),
                new SearchResultItem(
                        "Result 2 for: " + query,
                        "https://example.com/result2",
                        "This is a summary of the second search result for " + query + "."
                ),
                new SearchResultItem(
                        "Result 3 for: " + query,
                        "https://example.com/result3",
                        "This is a summary of the third search result for " + query + "."
                )
        );
        return new SearchResult(query, items, 3, java.time.Instant.now().toString());
    }
}
