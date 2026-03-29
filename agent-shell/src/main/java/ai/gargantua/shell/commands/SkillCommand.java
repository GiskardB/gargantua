package ai.gargantua.shell.commands;

import ai.gargantua.core.skill.SkillCard;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillRegistry;
import ai.gargantua.shell.renderer.StreamingRenderer;
import ai.gargantua.shell.renderer.TableRenderer;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Shell commands for skill management. Usage: {@code skill list}, {@code skill show <name>},
 * {@code skill reload}. Displays skill metadata in a formatted table.
 */
@Component
public class SkillCommand {

    private final SkillRegistry skillRegistry;
    private final StreamingRenderer renderer;
    private final TableRenderer tableRenderer;

    public SkillCommand(SkillRegistry skillRegistry,
                        StreamingRenderer renderer,
                        TableRenderer tableRenderer) {
        this.skillRegistry = skillRegistry;
        this.renderer = renderer;
        this.tableRenderer = tableRenderer;
    }

    @Command(name = {"skill", "list"}, description = "List all available skills in table format")
    public String list() {
        var skills = skillRegistry.listMeta();
        if (skills.isEmpty()) {
            return "No skills registered.";
        }

        var headers = List.of("Name", "Version", "Domain", "Source", "Active", "Schema");
        var rows = new ArrayList<List<String>>();

        for (SkillMeta meta : skills) {
            rows.add(List.of(
                    meta.name(),
                    meta.version() != null ? meta.version() : "-",
                    meta.domain() != null ? meta.domain() : "-",
                    meta.source() != null ? meta.source().name() : "-",
                    meta.active() ? "yes" : "no",
                    meta.hasSchema() ? "yes" : "no"
            ));
        }

        return tableRenderer.renderTable(headers, rows);
    }

    @Command(name = {"skill", "show"}, description = "Show detailed information about a specific skill")
    public String show(String name) {
        var card = skillRegistry.load(name);
        if (card == null) {
            return "Skill not found: " + name;
        }

        var sb = new StringBuilder();
        sb.append("""
                === Skill: %s ===
                Version:        %s
                Domain:         %s
                Source:         %s
                Active:         %s
                Has Schema:     %s
                Max Tokens:     %s
                Temperature:    %s
                Preferred Model:%s
                """.formatted(
                card.meta().name(),
                card.meta().version(),
                card.meta().domain(),
                card.meta().source(),
                card.meta().active(),
                card.meta().hasSchema(),
                card.maxTokens() != null ? card.maxTokens() : "default",
                card.temperature() != null ? card.temperature() : "default",
                card.preferredModel() != null ? card.preferredModel() : "default"));

        if (card.allowedTools() != null && !card.allowedTools().isEmpty()) {
            sb.append("Allowed Tools:  ").append(String.join(", ", card.allowedTools())).append('\n');
        }

        if (card.references() != null && !card.references().isEmpty()) {
            sb.append("References:     ").append(String.join(", ", card.references())).append('\n');
        }

        if (card.systemPrompt() != null) {
            sb.append("\n--- System Prompt ---\n");
            sb.append(card.systemPrompt()).append('\n');
        }

        if (card.outputSchema() != null) {
            sb.append("\n--- Output Schema ---\n");
            sb.append(card.outputSchema()).append('\n');
        }

        return sb.toString();
    }

    @Command(name = {"skill", "reload"}, description = "Trigger hot reload of all skills")
    public String reload() {
        try {
            skillRegistry.reload();
            List<SkillMeta> skills = skillRegistry.listMeta();
            return "Skills reloaded successfully. " + skills.size() + " skills available.";
        } catch (Exception e) {
            return "Failed to reload skills: " + e.getMessage();
        }
    }
}
