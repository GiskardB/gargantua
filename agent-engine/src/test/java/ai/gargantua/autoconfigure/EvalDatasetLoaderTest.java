package ai.gargantua.autoconfigure;

import ai.gargantua.core.eval.EvalCase;
import ai.gargantua.core.exception.EvalSuiteNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EvalDatasetLoader")
class EvalDatasetLoaderTest {

    @TempDir
    Path tempDir;

    private AgentProperties properties;
    private EvalDatasetLoader loader;

    @BeforeEach
    void setUp() {
        properties = new AgentProperties();
        properties.getSkill().setPath(tempDir.toString());
        properties.getEvals().setDatasetPath("evals");
        loader = new EvalDatasetLoader(properties);
    }

    @Test
    @DisplayName("loads eval cases from valid evals.json")
    void loadsEvalCases() throws IOException {
        Path evalDir = tempDir.resolve("my-skill/evals");
        Files.createDirectories(evalDir);
        Files.writeString(evalDir.resolve("evals.json"), """
                [
                  {
                    "id": "tc-1",
                    "description": "basic greeting",
                    "input": "Hello",
                    "expectedBehaviors": ["greet back"],
                    "forbiddenBehaviors": ["insult"],
                    "tags": ["smoke"]
                  },
                  {
                    "id": "tc-2",
                    "description": "farewell",
                    "input": "Goodbye",
                    "expectedBehaviors": ["say goodbye"],
                    "forbiddenBehaviors": [],
                    "tags": ["smoke", "polite"]
                  }
                ]
                """);

        List<EvalCase> cases = loader.load("my-skill");

        assertThat(cases).hasSize(2);
        assertThat(cases.get(0).id()).isEqualTo("tc-1");
        assertThat(cases.get(0).input()).isEqualTo("Hello");
        assertThat(cases.get(0).expectedBehaviors()).containsExactly("greet back");
        assertThat(cases.get(0).forbiddenBehaviors()).containsExactly("insult");
        assertThat(cases.get(0).tags()).containsExactly("smoke");

        assertThat(cases.get(1).id()).isEqualTo("tc-2");
        assertThat(cases.get(1).tags()).containsExactlyInAnyOrder("smoke", "polite");
    }

    @Test
    @DisplayName("throws EvalSuiteNotFoundException when eval file does not exist")
    void throwsWhenFileNotFound() {
        assertThatThrownBy(() -> loader.load("nonexistent-skill"))
                .isInstanceOf(EvalSuiteNotFoundException.class)
                .hasMessageContaining("nonexistent-skill");
    }

    @Test
    @DisplayName("throws RuntimeException when eval file contains invalid JSON")
    void throwsOnInvalidJson() throws IOException {
        Path evalDir = tempDir.resolve("bad-skill/evals");
        Files.createDirectories(evalDir);
        Files.writeString(evalDir.resolve("evals.json"), "not valid json {{{");

        assertThatThrownBy(() -> loader.load("bad-skill"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to load eval dataset for skill 'bad-skill'");
    }

    @Test
    @DisplayName("loads empty array from evals.json")
    void loadsEmptyArray() throws IOException {
        Path evalDir = tempDir.resolve("empty-skill/evals");
        Files.createDirectories(evalDir);
        Files.writeString(evalDir.resolve("evals.json"), "[]");

        List<EvalCase> cases = loader.load("empty-skill");
        assertThat(cases).isEmpty();
    }

    @Test
    @DisplayName("respects custom dataset path configuration")
    void respectsCustomDatasetPath() throws IOException {
        properties.getEvals().setDatasetPath("custom-evals");

        Path evalDir = tempDir.resolve("my-skill/custom-evals");
        Files.createDirectories(evalDir);
        Files.writeString(evalDir.resolve("evals.json"), """
                [{"id": "c1", "description": "test", "input": "hi",
                  "expectedBehaviors": [], "forbiddenBehaviors": [], "tags": []}]
                """);

        List<EvalCase> cases = loader.load("my-skill");
        assertThat(cases).hasSize(1);
        assertThat(cases.get(0).id()).isEqualTo("c1");
    }
}
