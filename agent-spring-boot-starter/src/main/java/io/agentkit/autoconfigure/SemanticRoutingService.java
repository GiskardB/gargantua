package io.agentkit.autoconfigure;

import io.agentkit.core.orchestrator.RoutingResult;
import io.agentkit.core.skill.SkillMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Semantic routing service. Pre-computes embeddings (placeholder: stores description hashes)
 * at boot and computes similarity for routing. Falls back to LLM routing if below threshold.
 */
@Component
public class SemanticRoutingService {

    private static final Logger log = LoggerFactory.getLogger(SemanticRoutingService.class);

    private final AgentProperties properties;
    private final RoutingService routingService;

    /** Placeholder: map of skill name to description words for simple text overlap. */
    private final Map<String, String[]> skillDescriptions = new HashMap<>();

    public SemanticRoutingService(AgentProperties properties, RoutingService routingService) {
        this.properties = properties;
        this.routingService = routingService;
    }

    /**
     * Index skill descriptions for routing. Call at boot or on reload.
     */
    public void index(List<SkillMeta> skills) {
        skillDescriptions.clear();
        for (SkillMeta skill : skills) {
            if (skill.active() && skill.description() != null) {
                skillDescriptions.put(skill.name(), tokenize(skill.description()));
            }
        }
        log.info("Indexed {} skills for semantic routing", skillDescriptions.size());
    }

    /**
     * Route a user message to the best matching skill.
     */
    public RoutingResult route(String userMessage, List<SkillMeta> skills) {
        if (skills == null || skills.isEmpty()) {
            return RoutingResult.semantic(properties.getRouting().getFallbackSkill(), 0.0);
        }

        // Ensure index is up to date
        if (skillDescriptions.isEmpty()) {
            index(skills);
        }

        double threshold = properties.getRouting().getSemantic().getThreshold();
        String[] messageTokens = tokenize(userMessage);

        String bestSkill = null;
        double bestScore = 0.0;

        for (Map.Entry<String, String[]> entry : skillDescriptions.entrySet()) {
            double score = cosineSimilarity(messageTokens, entry.getValue());
            if (score > bestScore) {
                bestScore = score;
                bestSkill = entry.getKey();
            }
        }

        if (bestSkill != null && bestScore >= threshold) {
            log.debug("Semantic match: skill='{}', score={}", bestSkill, bestScore);
            return RoutingResult.semantic(bestSkill, bestScore);
        }

        // Fall back to LLM routing if below threshold
        log.debug("Semantic score below threshold ({}), falling back to LLM routing", threshold);
        String llmResult = routingService.routeWithLlm(userMessage, skills);
        return RoutingResult.llm(llmResult);
    }

    /**
     * Simple cosine similarity using term overlap (placeholder for real embeddings).
     */
    double cosineSimilarity(String[] tokensA, String[] tokensB) {
        Map<String, Integer> freqA = termFrequency(tokensA);
        Map<String, Integer> freqB = termFrequency(tokensB);

        double dotProduct = 0;
        double normA = 0;
        double normB = 0;

        for (Map.Entry<String, Integer> e : freqA.entrySet()) {
            int a = e.getValue();
            int b = freqB.getOrDefault(e.getKey(), 0);
            dotProduct += a * b;
            normA += (double) a * a;
        }
        for (int b : freqB.values()) {
            normB += (double) b * b;
        }

        if (normA == 0 || normB == 0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private Map<String, Integer> termFrequency(String[] tokens) {
        Map<String, Integer> freq = new HashMap<>();
        for (String token : tokens) {
            freq.merge(token, 1, Integer::sum);
        }
        return freq;
    }

    private String[] tokenize(String text) {
        if (text == null || text.isBlank()) return new String[0];
        return text.toLowerCase().split("\\W+");
    }
}
