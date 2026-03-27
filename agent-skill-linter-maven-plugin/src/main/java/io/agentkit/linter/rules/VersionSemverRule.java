package io.agentkit.linter.rules;

import io.agentkit.linter.LintLevel;
import io.agentkit.linter.SkillLintResult;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Checks that the {@code version} field is valid semantic versioning.
 */
public class VersionSemverRule implements LintRule {

    private static final Pattern SEMVER = Pattern.compile(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)" +
            "(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)" +
            "(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?" +
            "(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$"
    );

    @Override
    public String name() {
        return "version-semver";
    }

    @Override
    public LintLevel defaultLevel() {
        return LintLevel.ERROR;
    }

    @Override
    public Optional<SkillLintResult> check(SkillLintInput input) {
        Object version = input.frontmatter().get("version");
        if (version == null) {
            return Optional.of(new SkillLintResult(
                    input.skillName(),
                    name(),
                    defaultLevel(),
                    "Missing 'version' field in frontmatter",
                    "SKILL.md",
                    1
            ));
        }
        String v = version.toString();
        if (!SEMVER.matcher(v).matches()) {
            return Optional.of(new SkillLintResult(
                    input.skillName(),
                    name(),
                    defaultLevel(),
                    "Version '%s' is not valid semver".formatted(v),
                    "SKILL.md",
                    1
            ));
        }
        return Optional.empty();
    }
}
