package ai.gargantua.linter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SkillLinterTest {

    @Test
    void shouldDetectNameMismatch() {
        File skillsDir = new File("src/test/resources/test-skills");
        // bad-name-skill has name: "wrong-name" which doesn't match the folder
        SkillLinter linter = new SkillLinter(List.of("name-matches-folder"));
        List<SkillLintResult> results = linter.lint(skillsDir);

        assertTrue(results.stream()
                        .anyMatch(r -> r.rule().equals("name-matches-folder")
                                && r.level() == LintLevel.ERROR
                                && r.skillName().equals("bad-name-skill")),
                "Expected name-matches-folder ERROR for bad-name-skill");
    }

    @Test
    void shouldWarnOnMissingEvals() {
        File skillsDir = new File("src/test/resources/test-skills");
        SkillLinter linter = new SkillLinter(List.of("evals-present"));
        List<SkillLintResult> results = linter.lint(skillsDir);

        assertTrue(results.stream()
                        .anyMatch(r -> r.rule().equals("evals-present")
                                && r.level() == LintLevel.WARNING
                                && r.skillName().equals("no-evals-skill")),
                "Expected evals-present WARNING for no-evals-skill");
    }

    @Test
    void shouldWarnOnLongDescription() {
        File skillsDir = new File("src/test/resources/test-skills");
        SkillLinter linter = new SkillLinter(List.of("description-length"));
        List<SkillLintResult> results = linter.lint(skillsDir);

        assertTrue(results.stream()
                        .anyMatch(r -> r.rule().equals("description-length")
                                && r.level() == LintLevel.WARNING
                                && r.skillName().equals("long-desc-skill")),
                "Expected description-length WARNING for long-desc-skill");
    }

    @Test
    void shouldPassCleanSkill(@TempDir Path tempDir) throws IOException {
        // Create an isolated clean skill to avoid noise from other test skills
        Path skillsRoot = tempDir.resolve("skills");
        Path cleanDir = skillsRoot.resolve("my-clean-skill");
        Path evalsDir = cleanDir.resolve("evals");
        Files.createDirectories(evalsDir);

        String skillMd = """
                ---
                name: my-clean-skill
                version: 1.0.0
                description: A well-formed skill for testing.
                metadata:
                  active: true
                ---
                # My Clean Skill

                This skill does useful things.
                """;
        Files.writeString(cleanDir.resolve("SKILL.md"), skillMd);
        Files.writeString(evalsDir.resolve("evals.json"), "[]");

        SkillLinter linter = new SkillLinter(null);
        List<SkillLintResult> results = linter.lint(skillsRoot.toFile());

        assertTrue(results.isEmpty(),
                "Expected no lint findings for a clean skill, but got: " + results);
    }
}
