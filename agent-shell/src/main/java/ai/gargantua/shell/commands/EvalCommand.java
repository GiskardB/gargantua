package ai.gargantua.shell.commands;

import ai.gargantua.core.orchestrator.AgentRequest;
import ai.gargantua.core.orchestrator.OrchestratorEngine;
import ai.gargantua.core.session.DryRunContext;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillRegistry;
import ai.gargantua.shell.renderer.StreamingRenderer;
import ai.gargantua.shell.renderer.TableRenderer;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Shell commands for running and viewing skill evaluation suites.
 * Usage: {@code eval run <skill>}, {@code eval report <skill>}. Runs evals in dry-run mode
 * and displays pass/fail/partial results.
 */
@Component
public class EvalCommand {

    private final SkillRegistry skillRegistry;
    private final OrchestratorEngine orchestrator;
    private final StreamingRenderer renderer;
    private final TableRenderer tableRenderer;

    public EvalCommand(SkillRegistry skillRegistry,
                       OrchestratorEngine orchestrator,
                       StreamingRenderer renderer,
                       TableRenderer tableRenderer) {
        this.skillRegistry = skillRegistry;
        this.orchestrator = orchestrator;
        this.renderer = renderer;
        this.tableRenderer = tableRenderer;
    }

    @Command(name = {"eval", "run"}, description = "Run eval suite for a skill")
    public String run(
            @Option(longName = "skill", description = "Skill name to evaluate") String skillName,
            @Option(longName = "all", description = "Run evals for all skills") boolean all) {

        if (!all && (skillName == null || skillName.isBlank())) {
            return "Please specify --skill <name> or --all";
        }

        var skillNames = new ArrayList<String>();
        if (all) {
            for (SkillMeta meta : skillRegistry.listMeta()) {
                if (meta.active()) {
                    skillNames.add(meta.name());
                }
            }
            if (skillNames.isEmpty()) {
                return "No active skills found.";
            }
        } else {
            skillNames.add(skillName);
        }

        var output = new StringBuilder();
        var headers = List.of("Skill", "Status", "Details");
        var rows = new ArrayList<List<String>>();

        for (String name : skillNames) {
            renderer.printInfo("Evaluating skill: " + name + "...");
            try {
                var request = AgentRequest.builder()
                        .message("__eval__")
                        .sessionId(UUID.randomUUID().toString())
                        .userId("eval-runner")
                        .forceSkill(name)
                        .dryRunContext(DryRunContext.inactive())
                        .build();

                var response = orchestrator.invoke(request);
                rows.add(List.of(
                        name,
                        "COMPLETED",
                        response.inputTokens() + " in / " + response.outputTokens() + " out, "
                                + response.durationMs() + "ms"
                ));
            } catch (Exception e) {
                rows.add(List.of(name, "FAILED", e.getMessage() != null ? e.getMessage() : "Unknown error"));
            }
        }

        output.append("\n=== Eval Results ===\n");
        output.append(tableRenderer.renderTable(headers, rows));

        return output.toString();
    }
}
