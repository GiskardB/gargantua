package io.agentkit.autoconfigure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentkit.core.eval.EvalCase;
import io.agentkit.core.exception.EvalSuiteNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Loads evaluation datasets from the skill directory.
 */
@Component
public class EvalDatasetLoader {

    private static final Logger log = LoggerFactory.getLogger(EvalDatasetLoader.class);

    private final AgentProperties properties;
    private final ObjectMapper objectMapper;

    public EvalDatasetLoader(AgentProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Load eval cases for a given skill name from evals/evals.json in the skill directory.
     */
    public List<EvalCase> load(String skillName) {
        String skillPath = properties.getSkill().getPath();
        Path evalFile = Path.of(skillPath, skillName, properties.getEvals().getDatasetPath(), "evals.json");

        if (!Files.exists(evalFile)) {
            throw new EvalSuiteNotFoundException(skillName);
        }

        try {
            String content = Files.readString(evalFile);
            List<EvalCase> cases = objectMapper.readValue(content, new TypeReference<>() {});
            log.info("Loaded {} eval cases for skill '{}'", cases.size(), skillName);
            return cases;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load eval dataset for skill '" + skillName + "'", e);
        }
    }
}
