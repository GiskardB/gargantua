package io.agentkit.linter.rules;

import io.agentkit.linter.LintLevel;
import io.agentkit.linter.SkillLintResult;

import java.io.File;
import java.util.Optional;

/**
 * Warns if the skill directory does not contain an {@code evals/} directory
 * or an {@code evals.json} file.
 */
public class EvalsPresentRule implements LintRule {

    @Override
    public String name() {
        return "evals-present";
    }

    @Override
    public LintLevel defaultLevel() {
        return LintLevel.WARNING;
    }

    @Override
    public Optional<SkillLintResult> check(SkillLintInput input) {
        File evalsDir = new File(input.skillDir(), "evals");
        File evalsJson = new File(input.skillDir(), "evals.json");

        if (!evalsDir.isDirectory() && !evalsJson.isFile()) {
            return Optional.of(new SkillLintResult(
                    input.skillName(),
                    name(),
                    defaultLevel(),
                    "No evals/ directory or evals.json found — consider adding evaluations",
                    "SKILL.md",
                    0
            ));
        }
        return Optional.empty();
    }
}
