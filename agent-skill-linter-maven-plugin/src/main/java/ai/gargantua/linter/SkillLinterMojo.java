package ai.gargantua.linter;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;
import java.util.List;

/**
 * Maven plugin goal that lints agent skill definitions.
 * Scans for SKILL.md files and validates them against configurable rules.
 */
@Mojo(
        name = "lint",
        defaultPhase = LifecyclePhase.VERIFY,
        requiresDependencyResolution = ResolutionScope.COMPILE
)
public class SkillLinterMojo extends AbstractMojo {

    /**
     * Directory containing skill subdirectories. Each subdirectory should
     * contain a SKILL.md file.
     */
    @Parameter(property = "skillLinter.skillsDirectory",
               defaultValue = "${project.basedir}/src/main/resources/skills")
    private File skillsDirectory;

    /**
     * Whether to fail the build on WARNING-level findings.
     */
    @Parameter(property = "skillLinter.failOnWarning", defaultValue = "false")
    private boolean failOnWarning;

    /**
     * List of rule names to enable. If empty, all rules are applied.
     */
    @Parameter(property = "skillLinter.rules")
    private List<String> rules;

    @Override
    public void execute() throws MojoFailureException {
        getLog().info("Linting skills in: " + skillsDirectory);

        if (!skillsDirectory.isDirectory()) {
            getLog().warn("Skills directory does not exist: " + skillsDirectory);
            return;
        }

        var linter = new SkillLinter(rules);
        var results = linter.lint(skillsDirectory);

        if (results.isEmpty()) {
            getLog().info("All skills passed linting.");
            return;
        }

        boolean hasErrors = false;
        boolean hasWarnings = false;

        for (var result : results) {
            var msg = "[%s] %s: %s (%s:%d)"
                    .formatted(result.level(), result.rule(), result.message(),
                               result.file(), result.line());

            if (result.level() == LintLevel.ERROR) {
                getLog().error(msg);
                hasErrors = true;
            } else {
                getLog().warn(msg);
                hasWarnings = true;
            }
        }

        getLog().info("Lint complete: %d finding(s)".formatted(results.size()));

        if (hasErrors) {
            throw new MojoFailureException(
                    "Skill linting failed with %d error(s)".formatted(
                            results.stream()
                                   .filter(r -> r.level() == LintLevel.ERROR)
                                   .count()));
        }

        if (failOnWarning && hasWarnings) {
            throw new MojoFailureException(
                    "Skill linting failed: failOnWarning is enabled and %d warning(s) found"
                            .formatted(results.stream()
                                              .filter(r -> r.level() == LintLevel.WARNING)
                                              .count()));
        }
    }
}
