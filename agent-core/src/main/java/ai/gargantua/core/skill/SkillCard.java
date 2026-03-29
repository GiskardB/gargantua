package ai.gargantua.core.skill;

import ai.gargantua.core.rag.RagConfig;

import java.util.List;

/**
 * Full skill definition loaded lazily when a skill is activated for a request.
 * Contains everything needed to execute the skill: system prompt, allowed tools,
 * output schema, LLM preferences, and reference documents.
 *
 * <p>Loaded from SKILL.md files by the {@link SkillRegistry}. The frontmatter
 * becomes {@link SkillMeta}, and the markdown body becomes the system prompt.</p>
 *
 * @param meta           lightweight metadata (name, description, version, etc.)
 * @param systemPrompt   the markdown body of SKILL.md, injected as the LLM system prompt
 * @param allowedTools   tool names this skill is permitted to invoke (empty = no tools)
 * @param outputSchema   optional JSON Schema string for output validation
 * @param references     additional context documents appended to the prompt
 * @param maxTokens      per-skill override for max output tokens (null = use global default)
 * @param temperature    per-skill override for LLM temperature (null = use global default)
 * @param preferredModel per-skill LLM model alias (null = use LLM router)
 * @param ragConfig      RAG configuration if skill declares knowledge-base (null = no RAG)
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
        RagConfig ragConfig
) {
}
