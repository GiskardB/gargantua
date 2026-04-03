package ai.gargantua.autoconfigure;

import ai.gargantua.core.tool.AgentTool;
import ai.gargantua.core.tool.RequiresApproval;
import ai.gargantua.core.tool.ToolDefinition;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Auto-discovers tools at boot by scanning all Spring beans for methods annotated
 * with {@link AgentTool}. Also reads {@link RequiresApproval} metadata to build
 * complete {@link ToolDefinition} descriptors.
 *
 * <p>The orchestrator uses {@link #getFilteredTools(List)} to restrict available tools
 * to those listed in the skill's {@code allowed-tools} frontmatter.</p>
 *
 * @see AgentTool
 * @see ToolDefinition
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final ApplicationContext applicationContext;
    private final Map<String, ToolDefinition> tools = new HashMap<>();

    public ToolRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void scan() {
        var beanNames = applicationContext.getBeanDefinitionNames();
        var count = 0;

        for (var beanName : beanNames) {
            Object bean;
            try {
                bean = applicationContext.getBean(beanName);
            } catch (Exception e) {
                continue;
            }

            for (var method : bean.getClass().getMethods()) {
                var annotation = method.getAnnotation(AgentTool.class);
                if (annotation == null) continue;

                var toolName = annotation.name().isBlank() ? method.getName() : annotation.name();
                var requiresApproval = method.isAnnotationPresent(RequiresApproval.class);
                var approvalMessage = "";
                var dangerous = false;

                if (requiresApproval) {
                    var approval = method.getAnnotation(RequiresApproval.class);
                    approvalMessage = approval.message();
                    dangerous = approval.dangerous();
                }

                var def = new ToolDefinition(
                        toolName,
                        annotation.description(),
                        annotation.parallelizable(),
                        requiresApproval,
                        false, // cacheable is determined by @CacheableToolResult
                        approvalMessage,
                        dangerous
                );

                tools.put(toolName, def);
                count++;
                log.debug("Registered tool: {}", toolName);
            }
        }

        log.info("ToolRegistry: scanned {} tool(s)", count);
    }

    public Collection<ToolDefinition> getToolDefinitions() {
        return tools.values();
    }

    public List<String> getAllToolNames() {
        return List.copyOf(tools.keySet());
    }

    /**
     * Get tool definitions filtered by the allowed tools list.
     * If allowedTools is null or empty, return all tools.
     */
    public Collection<ToolDefinition> getFilteredTools(List<String> allowedTools) {
        if (allowedTools == null || allowedTools.isEmpty()) {
            return tools.values();
        }
        return tools.entrySet().stream()
                .filter(e -> allowedTools.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .toList();
    }
}
