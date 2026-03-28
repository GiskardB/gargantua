package ai.gargantua.adapters.web;

import ai.gargantua.core.capabilities.AgentCapabilities;
import ai.gargantua.core.capabilities.SkillCapability;
import ai.gargantua.core.skill.SkillCard;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * REST endpoint exposing the agent's capabilities (registered skills, tools, metadata).
 * Response is cacheable for 60 seconds to reduce load during capability discovery.
 */
@RestController
@RequestMapping("/api/capabilities")
@Tag(name = "Capabilities")
public class CapabilitiesController {

    private final SkillRegistry skillRegistry;
    private final String agentId;
    private final String displayName;

    public CapabilitiesController(
            SkillRegistry skillRegistry,
            @Value("${agent.id:default-agent}") String agentId,
            @Value("${agent.display-name:AI Agent}") String displayName) {
        this.skillRegistry = skillRegistry;
        this.agentId = agentId;
        this.displayName = displayName;
    }

    @GetMapping
    @Operation(
            summary = "Get agent capabilities",
            description = "Returns the agent's capabilities including available skills and metadata."
    )
    @ApiResponse(responseCode = "200", description = "Agent capabilities")
    public ResponseEntity<AgentCapabilities> getCapabilities() {
        List<SkillMeta> allMeta = skillRegistry.listMeta();
        List<SkillCapability> capabilities = new ArrayList<>();

        for (SkillMeta meta : allMeta) {
            List<String> allowedTools = List.of();
            try {
                SkillCard card = skillRegistry.load(meta.name());
                allowedTools = card.allowedTools();
            } catch (Exception ignored) {
                // Fall back to empty tools list
            }
            capabilities.add(new SkillCapability(
                    meta.name(),
                    meta.description(),
                    meta.domain(),
                    meta.version(),
                    meta.active(),
                    meta.hasSchema(),
                    allowedTools
            ));
        }

        AgentCapabilities result = new AgentCapabilities(
                agentId,
                displayName,
                "AI Agent powered by AgentKit",
                true,
                capabilities,
                Instant.now()
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS))
                .body(result);
    }
}
