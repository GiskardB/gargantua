package ai.gargantua.autoconfigure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.gargantua.core.eval.EvalCase;
import ai.gargantua.core.exception.EvalSuiteNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Loads evaluation datasets from the skill directory.
 * Supports both filesystem paths and classpath resources (e.g., classpath:skills/).
 */
@Component
public class EvalDatasetLoader {

    private static final Logger log = LoggerFactory.getLogger(EvalDatasetLoader.class);

    private final AgentProperties properties;
    private final ObjectMapper objectMapper;

    public EvalDatasetLoader(AgentProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Load eval cases for a given skill name from evals/evals.json in the skill directory.
     */
    public List<EvalCase> load(String skillName) {
        String skillPath = properties.getSkill().getPath();
        String datasetSubPath = properties.getEvals().getDatasetPath();
        String relativePath = skillName + "/" + datasetSubPath + "/evals.json";

        try {
            String content;

            if (skillPath.startsWith("classpath:")) {
                // Load from classpath
                String classpathBase = skillPath.substring("classpath:".length());
                if (!classpathBase.endsWith("/")) classpathBase += "/";
                String resourcePath = classpathBase + relativePath;
                var resource = new ClassPathResource(resourcePath);
                if (!resource.exists()) {
                    throw new EvalSuiteNotFoundException(skillName);
                }
                try (InputStream is = resource.getInputStream()) {
                    content = new String(is.readAllBytes());
                }
                log.info("[Eval] Loaded eval dataset from classpath: {}", resourcePath);
            } else {
                // Load from filesystem
                Path evalFile = Path.of(skillPath, relativePath);
                if (!Files.exists(evalFile)) {
                    throw new EvalSuiteNotFoundException(skillName);
                }
                content = Files.readString(evalFile);
                log.info("[Eval] Loaded eval dataset from filesystem: {}", evalFile);
            }

            List<EvalCase> cases = objectMapper.readValue(content, new TypeReference<>() {});
            log.info("[Eval] Loaded {} eval cases for skill '{}'", cases.size(), skillName);
            return cases;

        } catch (EvalSuiteNotFoundException e) {
            throw e;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load eval dataset for skill '" + skillName + "'", e);
        }
    }
}
