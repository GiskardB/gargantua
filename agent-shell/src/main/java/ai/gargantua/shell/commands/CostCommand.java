package ai.gargantua.shell.commands;

import ai.gargantua.shell.renderer.StreamingRenderer;
import ai.gargantua.shell.renderer.TableRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shell commands for viewing token usage and cost statistics.
 * Usage: {@code cost summary}, {@code cost by-skill}. Displays data in formatted tables.
 */
@Component
public class CostCommand {

    private final StreamingRenderer renderer;
    private final TableRenderer tableRenderer;

    // In-memory cost tracking for shell sessions
    private final Map<String, SkillCostAccumulator> costBySkill = new ConcurrentHashMap<>();
    private final AtomicLong totalInputTokens = new AtomicLong(0);
    private final AtomicLong totalOutputTokens = new AtomicLong(0);
    private final AtomicInteger totalRequests = new AtomicInteger(0);

    public CostCommand(StreamingRenderer renderer, TableRenderer tableRenderer) {
        this.renderer = renderer;
        this.tableRenderer = tableRenderer;
    }

    @Command(name = {"cost", "summary"}, description = "Show cost summary for the current shell session")
    public String summary() {
        if (totalRequests.get() == 0) {
            return "No requests recorded yet. Start a chat session to track costs.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== Cost Summary ===\n\n");

        // Per-skill breakdown
        List<String> headers = List.of("Skill", "Requests", "Input Tokens", "Output Tokens", "Total Tokens");
        List<List<String>> rows = new ArrayList<>();

        for (Map.Entry<String, SkillCostAccumulator> entry : costBySkill.entrySet()) {
            SkillCostAccumulator acc = entry.getValue();
            long total = acc.inputTokens.get() + acc.outputTokens.get();
            rows.add(List.of(
                    entry.getKey(),
                    String.valueOf(acc.requests.get()),
                    String.valueOf(acc.inputTokens.get()),
                    String.valueOf(acc.outputTokens.get()),
                    String.valueOf(total)
            ));
        }

        sb.append(tableRenderer.renderTable(headers, rows));

        // Totals
        long totalIn = totalInputTokens.get();
        long totalOut = totalOutputTokens.get();
        sb.append("\nTotal Requests:      ").append(totalRequests.get()).append('\n');
        sb.append("Total Input Tokens:  ").append(totalIn).append('\n');
        sb.append("Total Output Tokens: ").append(totalOut).append('\n');
        sb.append("Total Tokens:        ").append(totalIn + totalOut).append('\n');

        return sb.toString();
    }

    /**
     * Record cost data from an agent response. Called by commands after receiving responses.
     */
    public void recordUsage(String skillName, int inputTokens, int outputTokens) {
        String key = skillName != null ? skillName : "(unknown)";
        costBySkill.computeIfAbsent(key, k -> new SkillCostAccumulator())
                .add(inputTokens, outputTokens);
        totalInputTokens.addAndGet(inputTokens);
        totalOutputTokens.addAndGet(outputTokens);
        totalRequests.incrementAndGet();
    }

    private static class SkillCostAccumulator {
        final AtomicInteger requests = new AtomicInteger(0);
        final AtomicLong inputTokens = new AtomicLong(0);
        final AtomicLong outputTokens = new AtomicLong(0);

        void add(int input, int output) {
            requests.incrementAndGet();
            inputTokens.addAndGet(input);
            outputTokens.addAndGet(output);
        }
    }
}
