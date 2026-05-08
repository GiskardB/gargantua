package ai.gargantua.core.skill;

import ai.gargantua.core.memory.MemoryLayer;
import ai.gargantua.core.rag.RagConfig;

import java.util.List;
import java.util.Set;

/**
 * Full skill definition loaded lazily when a skill is activated for a request.
 * Contains everything needed to execute the skill: system prompt, allowed tools,
 * output schema, LLM preferences, and reference documents.
 *
 * <p>Loaded from SKILL.md files by the {@link SkillRegistry}. The frontmatter
 * becomes {@link SkillMeta}, and the markdown body becomes the system prompt.</p>
 *
 * @param meta                  lightweight metadata (name, description, version, etc.)
 * @param systemPrompt          the markdown body of SKILL.md, injected as the LLM system prompt
 * @param allowedTools          tool names this skill is permitted to invoke (empty = no tools)
 * @param outputSchema          optional JSON Schema string for output validation
 * @param references            additional context documents appended to the prompt
 * @param maxTokens             per-skill override for max output tokens (null = use global default)
 * @param temperature           per-skill override for LLM temperature (null = use global default)
 * @param preferredModel        per-skill LLM model alias (null = use LLM router)
 * @param ragConfig             RAG configuration if skill declares knowledge-base (null = no RAG)
 * @param enabledMemoryLayers   memory layers fetched by the composer for this skill;
 *                              {@code null} (default) means fetch all layers
 * @param examples              example prompts surfaced via the A2A Agent Card for discovery;
 *                              may be empty
 *
 * @see SkillMeta
 * @see SkillRegistry
 */
public record SkillCard(
        SkillMeta meta,
        String systemPrompt,
        List<String> allowedTools,
        String outputSchema,
        List<String> references,
        Integer maxTokens,
        Double temperature,
        String preferredModel,
        RagConfig ragConfig,
        Set<MemoryLayer> enabledMemoryLayers,
        List<String> examples
) {
    public SkillCard {
        examples = examples == null ? List.of() : List.copyOf(examples);
    }

    /**
     * Convenience constructor that defaults {@code enabledMemoryLayers} to {@code null}
     * (i.e. fetch all memory layers) and {@code examples} to an empty list.
     * Preserves the historical 9-arg API.
     */
    public SkillCard(
            SkillMeta meta,
            String systemPrompt,
            List<String> allowedTools,
            String outputSchema,
            List<String> references,
            Integer maxTokens,
            Double temperature,
            String preferredModel,
            RagConfig ragConfig
    ) {
        this(meta, systemPrompt, allowedTools, outputSchema, references,
                maxTokens, temperature, preferredModel, ragConfig, null, List.of());
    }

    /**
     * Convenience constructor that defaults {@code examples} to an empty list.
     * Preserves the historical 10-arg API used by the SKILL.md parser before
     * examples wiring was added.
     */
    public SkillCard(
            SkillMeta meta,
            String systemPrompt,
            List<String> allowedTools,
            String outputSchema,
            List<String> references,
            Integer maxTokens,
            Double temperature,
            String preferredModel,
            RagConfig ragConfig,
            Set<MemoryLayer> enabledMemoryLayers
    ) {
        this(meta, systemPrompt, allowedTools, outputSchema, references,
                maxTokens, temperature, preferredModel, ragConfig, enabledMemoryLayers, List.of());
    }
}
