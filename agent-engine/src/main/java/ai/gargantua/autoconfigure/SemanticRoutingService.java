package ai.gargantua.autoconfigure;

import ai.gargantua.core.orchestrator.RoutingResult;
import ai.gargantua.core.routing.ClassificationResult;
import ai.gargantua.core.routing.SkillClassifier;
import ai.gargantua.core.skill.SkillMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

/**
 * Hybrid skill routing service: delegates local matching to a pluggable
 * {@link SkillClassifier} (in-process, no network I/O — semantic embeddings by
 * default, see {@code agent.routing.classifier.engine}), with LLM fallback via
 * {@link RoutingService} when classifier confidence is below threshold.
 *
 * @see RoutingService
 * @see ai.gargantua.core.orchestrator.RoutingResult
 */
public class SemanticRoutingService {

    private static final Logger log = LoggerFactory.getLogger(SemanticRoutingService.class);

    private final AgentProperties properties;
    private final RoutingService routingService;
    private final SkillClassifier classifier;

    public SemanticRoutingService(AgentProperties properties, RoutingService routingService,
                                   SkillClassifier classifier) {
        this.properties = properties;
        this.routingService = routingService;
        this.classifier = classifier;
    }

    /**
     * Index skills into the underlying classifier. Call at boot or on skill reload.
     */
    public void index(List<SkillMeta> skills) {
        classifier.index(skills);
    }

    /**
     * Route a user message to the best matching skill, branching on
     * {@code agent.routing.strategy}: {@code semantic} (classifier only),
     * {@code llm} (LLM-only), or {@code hybrid} (default — classifier with
     * LLM fallback when below threshold).
     */
    public RoutingResult route(String userMessage, List<SkillMeta> skills) {
        if (skills == null || skills.isEmpty()) {
            return RoutingResult.semantic(properties.getRouting().getFallbackSkill(), 0.0);
        }

        String strategy = normalizeStrategy(properties.getRouting().getStrategy());

        if ("llm".equals(strategy)) {
            log.debug("Routing strategy=llm — skipping local classifier");
            String llmResult = routingService.routeWithLlm(userMessage, skills);
            return RoutingResult.llm(llmResult);
        }

        double threshold = properties.getRouting().getClassifier().getThreshold();
        ClassificationResult result = classifier.classify(userMessage, skills);

        if (result.skillName() != null && result.confidence() >= threshold) {
            if (log.isDebugEnabled()) {
                log.debug("Classifier match: engine='{}', skill='{}', score={}",
                        classifier.engine(), result.skillName(), "%.4f".formatted(result.confidence()));
            }
            return RoutingResult.semantic(result.skillName(), result.confidence());
        }

        if ("semantic".equals(strategy)) {
            // Strict classifier strategy — no LLM fallback. Return the configured fallback skill.
            String fallback = properties.getRouting().getFallbackSkill();
            if (log.isDebugEnabled()) {
                log.debug("Classifier strategy: score below threshold ({} < {}), returning fallback skill '{}'",
                        result.confidence() >= 0 ? "%.4f".formatted(result.confidence()) : "none", threshold, fallback);
            }
            return RoutingResult.semantic(fallback, Math.max(0.0, result.confidence()));
        }

        // hybrid (default) — fall back to LLM routing
        if (log.isDebugEnabled()) {
            log.debug("Hybrid strategy: score below threshold ({} < {}), falling back to LLM routing",
                    result.confidence() >= 0 ? "%.4f".formatted(result.confidence()) : "none", threshold);
        }
        String llmResult = routingService.routeWithLlm(userMessage, skills);
        return RoutingResult.llm(llmResult);
    }

    private String normalizeStrategy(String raw) {
        if (raw == null || raw.isBlank()) return "hybrid";
        String s = raw.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (s) {
            case "semantic", "llm", "hybrid" -> s;
            default -> {
                log.warn("Unknown routing strategy '{}' — falling back to 'hybrid'", raw);
                yield "hybrid";
            }
        };
    }
}
