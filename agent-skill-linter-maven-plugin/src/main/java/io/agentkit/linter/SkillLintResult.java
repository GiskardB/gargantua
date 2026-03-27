package io.agentkit.linter;

/**
 * Represents a single lint finding for an agent skill.
 *
 * @param skillName the name of the skill being linted
 * @param rule      the rule that produced this result
 * @param level     severity level (ERROR or WARNING)
 * @param message   human-readable description of the finding
 * @param file      the file where the issue was found
 * @param line      the line number (0 if not applicable)
 */
public record SkillLintResult(
        String skillName,
        String rule,
        LintLevel level,
        String message,
        String file,
        int line
) {}
