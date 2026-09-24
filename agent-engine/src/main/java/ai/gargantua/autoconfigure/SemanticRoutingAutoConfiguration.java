package ai.gargantua.autoconfigure;

import ai.gargantua.core.routing.SkillClassifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;

/**
 * Auto-configuration for skill routing: the pluggable {@link SkillClassifier} engine
 * (see {@code agent.routing.classifier.engine}) and the LLM routing fallback.
 */
@AutoConfiguration
@EnableConfigurationProperties(AgentProperties.class)
public class SemanticRoutingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RoutingService.class)
    public RoutingService routingService(AgentProperties properties, LlmProviderFactory llmProviderFactory) {
        return new RoutingService(properties, llmProviderFactory);
    }

    @Bean
    @ConditionalOnMissingBean(SkillClassifier.class)
    @ConditionalOnProperty(name = "agent.routing.classifier.engine", havingValue = "tribuo")
    public SkillClassifier tribuoSkillClassifier(AgentProperties properties, ResourceLoader resourceLoader) {
        return new TribuoClassifier(resourceLoader.getResource(properties.getRouting().getClassifier().getTribuo().getModelPath()));
    }

    @Bean
    @ConditionalOnMissingBean(SkillClassifier.class)
    @ConditionalOnProperty(name = "agent.routing.classifier.engine", havingValue = "onnx")
    public SkillClassifier onnxSkillClassifier(AgentProperties properties, ResourceLoader resourceLoader) {
        var onnxProps = properties.getRouting().getClassifier().getOnnx();
        return new OnnxClassifier(resourceLoader.getResource(onnxProps.getModelPath()), onnxProps.getLabels());
    }

    @Bean
    @ConditionalOnMissingBean(SkillClassifier.class)
    @ConditionalOnProperty(name = "agent.routing.classifier.engine", havingValue = "semantic", matchIfMissing = true)
    public SkillClassifier semanticSkillClassifier() {
        return new SemanticSimilarityClassifier();
    }

    @Bean
    @ConditionalOnMissingBean(SemanticRoutingService.class)
    public SemanticRoutingService semanticRoutingService(AgentProperties properties,
                                                         RoutingService routingService,
                                                         SkillClassifier skillClassifier) {
        return new SemanticRoutingService(properties, routingService, skillClassifier);
    }
}
