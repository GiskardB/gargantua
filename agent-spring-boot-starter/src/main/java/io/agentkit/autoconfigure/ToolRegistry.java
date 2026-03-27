package io.agentkit.autoconfigure;

import io.agentkit.core.tool.AgentTool;
import io.agentkit.core.tool.RequiresApproval;
import io.agentkit.core.tool.ToolDefinition;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Scans Spring beans at boot for methods annotated with @AgentTool
 * and builds a registry of tool definitions.
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
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        int count = 0;

        for (String beanName : beanNames) {
            Object bean;
            try {
                bean = applicationContext.getBean(beanName);
            } catch (Exception e) {
                continue;
            }

            Class<?> beanClass = bean.getClass();
            for (Method method : beanClass.getMethods()) {
                AgentTool annotation = method.getAnnotation(AgentTool.class);
                if (annotation == null) continue;

                String toolName = annotation.name().isBlank() ? method.getName() : annotation.name();
                boolean requiresApproval = method.isAnnotationPresent(RequiresApproval.class);
                String approvalMessage = "";
                boolean dangerous = false;

                if (requiresApproval) {
                    RequiresApproval approval = method.getAnnotation(RequiresApproval.class);
                    approvalMessage = approval.message();
                    dangerous = approval.dangerous();
                }

                ToolDefinition def = new ToolDefinition(
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
                .collect(Collectors.toList());
    }
}
