package ai.gargantua.core.routing;

import ai.gargantua.core.skill.SkillMeta;

import java.util.List;

/**
 * Local, in-process classifier that maps a user message to a skill name.
 * Implementations must not perform network I/O — inference runs embedded,
 * pluggable via {@code agent.routing.classifier.engine}.
 *
 * @see ClassificationResult
 */
public interface SkillClassifier {

    /**
     * (Re)builds any internal index/state from the current skill set.
     * Called at boot and whenever the skill registry reloads.
     */
    void index(List<SkillMeta> skills);

    /**
     * Classifies a message against the indexed skills.
     */
    ClassificationResult classify(String userMessage, List<SkillMeta> skills);

    /**
     * Engine identifier for logging, e.g. {@code "semantic"}, {@code "onnx"}, {@code "tribuo"}.
     */
    String engine();
}
