package ai.gargantua.autoconfigure;

import ai.gargantua.core.flow.AgentsFlow;
import ai.gargantua.core.flow.FlowDefinition;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Discovers and registers all {@link AgentsFlow}-annotated methods at startup.
 * Each flow defines a multi-step pipeline of skills.
 */
@Component
public class FlowRegistry {

    private static final Logger log = LoggerFactory.getLogger(FlowRegistry.class);
    private final ApplicationContext applicationContext;
    private final Map<String, FlowDefinition> flows = new ConcurrentHashMap<>();

    public FlowRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void discoverFlows() {
        for (var beanName : applicationContext.getBeanDefinitionNames()) {
            Object bean;
            try { bean = applicationContext.getBean(beanName); }
            catch (Exception e) { continue; }

            for (Method method : bean.getClass().getMethods()) {
                var annotation = method.getAnnotation(AgentsFlow.class);
                if (annotation == null) continue;

                var flow = new FlowDefinition(annotation.name(), annotation.description());
                try {
                    method.invoke(bean, flow);
                    flows.put(flow.name(), flow);
                    log.info("[FlowRegistry] Registered flow '{}' with {} steps: {}",
                            flow.name(), flow.steps().size(),
                            flow.steps().stream().map(FlowDefinition.FlowStep::skillName).toList());
                } catch (Exception e) {
                    log.error("[FlowRegistry] Failed to register flow '{}': {}", annotation.name(), e.getMessage());
                }
            }
        }
        log.info("[FlowRegistry] Discovered {} flows", flows.size());
    }

    public Optional<FlowDefinition> get(String name) {
        return Optional.ofNullable(flows.get(name));
    }

    public List<FlowDefinition> getAll() {
        return List.copyOf(flows.values());
    }
}
