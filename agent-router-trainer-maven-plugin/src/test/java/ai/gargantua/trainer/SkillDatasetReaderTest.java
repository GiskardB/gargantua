package ai.gargantua.trainer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SkillDatasetReaderTest {

    @Test
    void collectsExamplesRoutingHintsAndDescription(@TempDir Path tempDir) throws IOException {
        Path skillDir = tempDir.resolve("spending-analysis");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: spending-analysis
                description: Analyzes personal spending by category and period.
                version: 1.0.0
                allowed-tools: [getTransactions]
                examples:
                  - how much did I spend this month
                  - show me spending by category
                metadata:
                  active: true
                  routing-hints: [spending, budget]
                ---
                System prompt body.
                """);

        Map<String, List<String>> bySkill = new SkillDatasetReader().readGrouped(tempDir.toFile());

        assertTrue(bySkill.containsKey("spending-analysis"));
        List<String> phrases = bySkill.get("spending-analysis");
        assertEquals(5, phrases.size());
        assertTrue(phrases.contains("how much did I spend this month"));
        assertTrue(phrases.contains("spending"));
        assertTrue(phrases.contains("Analyzes personal spending by category and period."));
    }

    @Test
    void skipsInactiveSkills(@TempDir Path tempDir) throws IOException {
        Path skillDir = tempDir.resolve("disabled-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: disabled-skill
                description: Not active.
                version: 1.0.0
                metadata:
                  active: false
                ---
                Body.
                """);

        Map<String, List<String>> bySkill = new SkillDatasetReader().readGrouped(tempDir.toFile());

        assertFalse(bySkill.containsKey("disabled-skill"));
    }
}
