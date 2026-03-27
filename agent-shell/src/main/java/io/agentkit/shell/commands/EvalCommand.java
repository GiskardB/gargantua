package io.agentkit.shell.commands;

import io.agentkit.core.orchestrator.AgentRequest;
import io.agentkit.core.orchestrator.AgentResponse;
import io.agentkit.core.orchestrator.OrchestratorEngine;
import io.agentkit.core.session.DryRunContext;
import io.agentkit.core.skill.SkillMeta;
import io.agentkit.core.skill.SkillRegistry;
import io.agentkit.shell.renderer.StreamingRenderer;
import io.agentkit.shell.renderer.TableRenderer;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@Command(command = "eval")
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

    @Command(command = "run", description = "Run eval suite for a skill")
    public String run(
            @Option(longNames = "skill", shortNames = 's', description = "Skill name to evaluate") String skillName,
            @Option(longNames = "all", description = "Run evals for all skills") boolean all) {

        if (!all && (skillName == null || skillName.isBlank())) {
            return "Please specify --skill <name> or --all";
        }

        List<String> skillNames = new ArrayList<>();
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

        StringBuilder output = new StringBuilder();
        List<String> headers = List.of("Skill", "Status", "Details");
        List<List<String>> rows = new ArrayList<>();

        for (String name : skillNames) {
            renderer.printInfo("Evaluating skill: " + name + "...");
            try {
                AgentRequest request = AgentRequest.builder()
                        .message("__eval__")
                        .sessionId(UUID.randomUUID().toString())
                        .userId("eval-runner")
                        .forceSkill(name)
                        .dryRunContext(DryRunContext.inactive())
                        .build();

                AgentResponse response = orchestrator.invoke(request);
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
