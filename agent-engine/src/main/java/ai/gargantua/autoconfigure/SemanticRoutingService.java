package ai.gargantua.autoconfigure;

import ai.gargantua.core.orchestrator.RoutingResult;
import ai.gargantua.core.skill.SkillMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hybrid skill routing service using term-frequency cosine similarity
 * for semantic matching, with LLM fallback when the similarity score is below threshold.
 *
 * <p>No external dependencies -- uses simple TF-based cosine similarity over tokenized text.</p>
 *
 * @see RoutingService
 * @see ai.gargantua.core.orchestrator.RoutingResult
 */
public class SemanticRoutingService {

    private static final Logger log = LoggerFactory.getLogger(SemanticRoutingService.class);

    private final AgentProperties properties;
    private final RoutingService routingService;

    /** Cached skill description tokens: skill name -> tokenized description. */
    private final Map<String, String[]> skillTokens = new ConcurrentHashMap<>();

    public SemanticRoutingService(AgentProperties properties, RoutingService routingService) {
        this.properties = properties;
        this.routingService = routingService;
        log.info("Initialized term-frequency semantic routing (no external models)");
    }

    /**
     * Index skill descriptions by tokenizing them. Call at boot or on skill reload.
     */
    public void index(List<SkillMeta> skills) {
        skillTokens.clear();
        for (SkillMeta skill : skills) {
            if (skill.active() && skill.description() != null && !skill.description().isBlank()) {
                skillTokens.put(skill.name(), tokenize(skill.description()));
            }
        }
        log.info("Indexed {} skills for semantic routing (term-frequency)", skillTokens.size());
    }

    /**
     * Route a user message to the best matching skill using term-frequency cosine
     * similarity, falling back to LLM routing if below threshold.
     */
    public RoutingResult route(String userMessage, List<SkillMeta> skills) {
        if (skills == null || skills.isEmpty()) {
            return RoutingResult.semantic(properties.getRouting().getFallbackSkill(), 0.0);
        }

        // Ensure index is up to date
        if (skillTokens.isEmpty()) {
            index(skills);
        }

        double threshold = properties.getRouting().getSemantic().getThreshold();

        // Tokenize the user message
        String[] messageTokens = tokenize(userMessage);

        String bestSkill = null;
        double bestScore = -1.0;

        for (Map.Entry<String, String[]> entry : skillTokens.entrySet()) {
            double score = cosineSimilarity(messageTokens, entry.getValue());
            log.trace("Semantic score for skill '{}': {}", entry.getKey(), String.format("%.4f", score));
            if (score > bestScore) {
                bestScore = score;
                bestSkill = entry.getKey();
            }
        }

        if (bestSkill != null && bestScore >= threshold) {
            log.debug("Semantic match: skill='{}', score={}", bestSkill, String.format("%.4f", bestScore));
            return RoutingResult.semantic(bestSkill, bestScore);
        }

        // Fall back to LLM routing
        log.debug("Semantic score below threshold ({} < {}), falling back to LLM routing",
                bestScore >= 0 ? String.format("%.4f", bestScore) : "none", threshold);
        String llmResult = routingService.routeWithLlm(userMessage, skills);
        return RoutingResult.llm(llmResult);
    }

    /**
     * Compute cosine similarity between two token arrays using term frequency vectors.
     * Visible for testing.
     */
    double cosineSimilarity(String[] a, String[] b) {
        // Build term frequency maps
        Map<String, Integer> tfA = termFrequency(a);
        Map<String, Integer> tfB = termFrequency(b);

        // Union of all terms
        Set<String> allTerms = new HashSet<>(tfA.keySet());
        allTerms.addAll(tfB.keySet());

        double dotProduct = 0, normA = 0, normB = 0;
        for (String term : allTerms) {
            int freqA = tfA.getOrDefault(term, 0);
            int freqB = tfB.getOrDefault(term, 0);
            dotProduct += (double) freqA * freqB;
            normA += (double) freqA * freqA;
            normB += (double) freqB * freqB;
        }

        if (normA == 0 || normB == 0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private Map<String, Integer> termFrequency(String[] tokens) {
        Map<String, Integer> tf = new HashMap<>();
        for (String token : tokens) {
            tf.merge(token, 1, Integer::sum);
        }
        return tf;
    }

    private String[] tokenize(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .trim()
                .split("\\s+");
    }
}
