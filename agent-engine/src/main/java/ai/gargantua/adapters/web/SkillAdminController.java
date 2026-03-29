package ai.gargantua.adapters.web;

import ai.gargantua.core.skill.SkillCard;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
@Tag(name = "Admin \u2014 Skills")
public class SkillAdminController {

    private final SkillRegistry skillRegistry;

    public SkillAdminController(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }

    @GetMapping
    @Operation(summary = "List all skills", description = "Returns all registered skills with their source information.")
    @ApiResponse(responseCode = "200", description = "List of skills")
    public ResponseEntity<List<SkillMeta>> listSkills() {
        return ResponseEntity.ok(skillRegistry.listMeta());
    }

    @GetMapping("/{skillName}")
    @Operation(summary = "Get skill detail", description = "Returns full skill card including system prompt and configuration.")
    @ApiResponse(responseCode = "200", description = "Skill detail")
    @ApiResponse(responseCode = "404", description = "Skill not found")
    public ResponseEntity<SkillCard> getSkill(@PathVariable String skillName) {
        return ResponseEntity.ok(skillRegistry.load(skillName));
    }

    @PostMapping("/reload")
    @Operation(summary = "Reload skills", description = "Triggers a reload of all skill registries.")
    @ApiResponse(responseCode = "200", description = "Skills reloaded")
    public ResponseEntity<Map<String, String>> reloadSkills() {
        skillRegistry.reload();
        return ResponseEntity.ok(Map.of("status", "reloaded"));
    }
}
