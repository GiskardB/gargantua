package ai.gargantua.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Reference implementation of a Gargantua AI agent. Demonstrates how to wire
 * tools, skills, and the orchestrator using Spring Boot auto-configuration.
 * See the {@code tools} package for example tool implementations.
 */
@SpringBootApplication
public class ExampleAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExampleAgentApplication.class, args);
    }
}
