package ai.gargantua.shell;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Shell CLI application for interacting with the Gargantua agent.
 * Runs in non-web mode. Supports embedded mode (direct orchestrator calls)
 * and remote mode (HTTP calls to a running agent server).
 *
 * In embedded mode, AgentMemoryAutoConfiguration is excluded because
 * EmbeddedProfileAutoConfiguration provides in-memory replacements.
 */
@SpringBootApplication(exclude = {
    ai.gargantua.memory.autoconfigure.AgentMemoryAutoConfiguration.class
})
public class AgentShellApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(AgentShellApplication.class);
        app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
        app.run(args);
    }
}
