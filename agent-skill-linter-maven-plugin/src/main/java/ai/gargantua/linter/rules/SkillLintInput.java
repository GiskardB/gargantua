package ai.gargantua.linter.rules;

import java.io.File;
import java.util.Map;

/**
 * Input data provided to each lint rule for evaluation.
 *
 * @param skillName  the skill name extracted from frontmatter
 * @param folderName the folder name containing the skill
 * @param frontmatter parsed YAML frontmatter as a map
 * @param body       the markdown body (after frontmatter)
 * @param skillDir   the skill directory on disk
 */
public record SkillLintInput(
        String skillName,
        String folderName,
        Map<String, Object> frontmatter,
        String body,
        File skillDir
) {}
