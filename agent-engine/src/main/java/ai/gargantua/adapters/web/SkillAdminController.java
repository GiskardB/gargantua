package ai.gargantua.adapters.web;

import ai.gargantua.core.skill.SkillCard;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Admin REST endpoint for managing skills. Lists metadata, loads full skill cards,
 * and triggers manual reloads of the skill registry.
 */
@RestController
@RequestMapping("/api/admin/skills")
@Tag(
        name = "Admin — Skills",
        description = "Inspect the live `SkillRegistry` and trigger reloads. Reflects the active "
                + "composite chain (filesystem + classpath-jar + annotated). Disabled skills "
                + "(`metadata.active=false`) are still returned by `GET /api/admin/skills` so operators "
                + "can audit them, but the router never picks them."
)
public class SkillAdminController {

    private final SkillRegistry skillRegistry;

    public SkillAdminController(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }

    @GetMapping
    @Operation(
            summary = "List every registered skill (metadata only)",
            description = "Returns the lightweight `SkillMeta` for every skill in the composite registry — "
                    + "name, description, version, active flag, dangerous flag, domain and source "
                    + "(`FILESYSTEM` / `CLASSPATH_JAR` / `ANNOTATION`). The full skill card (system prompt, "
                    + "references, RAG config, …) is loaded lazily by `GET /{skillName}`."
    )
    @ApiResponse(responseCode = "200", description = "List of `SkillMeta`.")
    public ResponseEntity<List<SkillMeta>> listSkills() {
        return ResponseEntity.ok(skillRegistry.listMeta());
    }

    @GetMapping("/{skillName}")
    @Operation(
            summary = "Get the full skill card",
            description = "Loads (and caches) the complete `SkillCard` — system prompt, references, "
                    + "allowedTools, RAG config, output schema, examples. Use this to debug what the "
                    + "LLM actually sees when this skill is activated."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Full `SkillCard`."),
            @ApiResponse(responseCode = "404", description = "No skill with this name in any registry.")
    })
    public ResponseEntity<SkillCard> getSkill(
            @Parameter(description = "Skill name as it appears in `SKILL.md` frontmatter (and the folder name).",
                    example = "billing-skill")
            @PathVariable String skillName) {
        return ResponseEntity.ok(skillRegistry.load(skillName));
    }

    @PostMapping("/reload")
    @Operation(
            summary = "Reload every skill registry",
            description = "Synchronously re-scans every backing registry — filesystem, classpath JARs, "
                    + "and `@AgentSkill` annotations — and refreshes the cache decorator. Use after editing "
                    + "a SKILL.md file in a long-running process when `agent.skill.hot-reload=false` "
                    + "(the default). With hot-reload enabled, the `WatchService` already does this for you."
    )
    @ApiResponse(responseCode = "200", description = "Skills reloaded — body carries `{\"status\":\"reloaded\"}`.")
    public ResponseEntity<Map<String, String>> reloadSkills() {
        skillRegistry.reload();
        return ResponseEntity.ok(Map.of("status", "reloaded"));
    }
}
