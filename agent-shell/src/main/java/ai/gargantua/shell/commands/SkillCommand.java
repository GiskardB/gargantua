package ai.gargantua.shell.commands;

import ai.gargantua.core.skill.SkillCard;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillRegistry;
import ai.gargantua.shell.renderer.StreamingRenderer;
import ai.gargantua.shell.renderer.TableRenderer;
import org.springframework.shell.command.annotation.Command;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Command(command = "skill")
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

    @Command(command = "list", description = "List all available skills in table format")
    public String list() {
        List<SkillMeta> skills = skillRegistry.listMeta();
        if (skills.isEmpty()) {
            return "No skills registered.";
        }

        List<String> headers = List.of("Name", "Version", "Domain", "Source", "Active", "Schema");
        List<List<String>> rows = new ArrayList<>();

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

    @Command(command = "show", description = "Show detailed information about a specific skill")
    public String show(String name) {
        SkillCard card = skillRegistry.load(name);
        if (card == null) {
            return "Skill not found: " + name;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== Skill: ").append(card.meta().name()).append(" ===\n");
        sb.append("Version:        ").append(card.meta().version()).append('\n');
        sb.append("Domain:         ").append(card.meta().domain()).append('\n');
        sb.append("Source:         ").append(card.meta().source()).append('\n');
        sb.append("Active:         ").append(card.meta().active()).append('\n');
        sb.append("Has Schema:     ").append(card.meta().hasSchema()).append('\n');
        sb.append("Max Tokens:     ").append(card.maxTokens() != null ? card.maxTokens() : "default").append('\n');
        sb.append("Temperature:    ").append(card.temperature() != null ? card.temperature() : "default").append('\n');
        sb.append("Preferred Model:").append(card.preferredModel() != null ? card.preferredModel() : "default").append('\n');

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

    @Command(command = "reload", description = "Trigger hot reload of all skills")
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
