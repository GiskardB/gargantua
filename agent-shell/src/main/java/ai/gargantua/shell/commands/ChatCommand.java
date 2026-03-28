package ai.gargantua.shell.commands;

import ai.gargantua.shell.client.AgentClient;
import ai.gargantua.shell.client.AgentClient.ShellChatRequest;
import ai.gargantua.shell.client.AgentClient.SseEvent;
import ai.gargantua.shell.renderer.StreamingRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Shell command for interactive chat with the agent. Usage: {@code chat "your message"}.
 * Supports streaming output, dry-run mode, and forced skill selection.
 */
@Component
@Command
public class ChatCommand {

    private final AgentClient agentClient;
    private final StreamingRenderer renderer;
    private final String defaultUserId;
    private final boolean showMeta;
    private final boolean showTiming;

    private String sessionId;
    private String userId;
    private boolean dryRun = false;
    private String forceSkill = null;
    private final List<String> history = new ArrayList<>();

    public ChatCommand(
            AgentClient agentClient,
            StreamingRenderer renderer,
            @Value("${agent.shell.user-id:dev-user}") String defaultUserId,
            @Value("${agent.shell.show-meta:true}") boolean showMeta,
            @Value("${agent.shell.show-timing:true}") boolean showTiming) {
        this.agentClient = agentClient;
        this.renderer = renderer;
        this.defaultUserId = defaultUserId;
        this.showMeta = showMeta;
        this.showTiming = showTiming;
        this.userId = defaultUserId;
    }

    @Command(name = "chat", description = "Start interactive chat session")
    public void chat() {
        sessionId = agentClient.newSession();
        renderer.printChatHeader(sessionId, userId, dryRun);

        Scanner scanner = new Scanner(System.in);
        while (true) {
            renderer.printToken("agent> ");
            if (!scanner.hasNextLine()) {
                break;
            }
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                continue;
            }

            if (input.startsWith("\\")) {
                boolean shouldExit = handleSlashCommand(input);
                if (shouldExit) {
                    break;
                }
                continue;
            }

            history.add(input);
            sendMessage(input);
        }

        renderer.printInfo("Session ended.");
    }

    private boolean handleSlashCommand(String input) {
        String[] parts = input.split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1] : null;

        switch (cmd) {
            case "\\exit", "\\quit" -> {
                return true;
            }
            case "\\new" -> {
                sessionId = agentClient.newSession();
                history.clear();
                forceSkill = null;
                dryRun = false;
                renderer.printInfo("New session started: " + sessionId);
            }
            case "\\dry" -> {
                dryRun = !dryRun;
                renderer.printInfo("Dry run mode: " + (dryRun ? "ON" : "OFF"));
            }
            case "\\skill" -> {
                if (arg != null && !arg.isBlank()) {
                    forceSkill = arg;
                    renderer.printInfo("Skill forced: " + forceSkill);
                } else {
                    forceSkill = null;
                    renderer.printInfo("Skill forcing cleared.");
                }
            }
            case "\\history" -> {
                if (history.isEmpty()) {
                    renderer.printMeta("No history yet.");
                } else {
                    for (int i = 0; i < history.size(); i++) {
                        renderer.println((i + 1) + ". " + history.get(i));
                    }
                }
            }
            case "\\info" -> {
                renderer.printInfo("Session:     " + sessionId);
                renderer.printInfo("User:        " + userId);
                renderer.printInfo("Dry Run:     " + dryRun);
                renderer.printInfo("Force Skill: " + (forceSkill != null ? forceSkill : "(none)"));
                renderer.printInfo("History:     " + history.size() + " messages");
            }
            case "\\clear" -> {
                // Print enough newlines to simulate clear
                for (int i = 0; i < 50; i++) {
                    renderer.println();
                }
                renderer.printChatHeader(sessionId, userId, dryRun);
            }
            case "\\help" -> {
                renderer.println("Available commands:");
                renderer.println("  \\exit     - Exit the chat session");
                renderer.println("  \\new      - Start a new session");
                renderer.println("  \\dry      - Toggle dry run mode");
                renderer.println("  \\skill <name> - Force a specific skill (empty to clear)");
                renderer.println("  \\history  - Show message history");
                renderer.println("  \\info     - Show session info");
                renderer.println("  \\clear    - Clear screen");
                renderer.println("  \\help     - Show this help");
            }
            default -> renderer.printError("Unknown command: " + cmd + ". Type \\help for available commands.");
        }
        return false;
    }

    private void sendMessage(String message) {
        long startTime = System.currentTimeMillis();

        ShellChatRequest request = new ShellChatRequest(
                message, sessionId, userId, dryRun, forceSkill);

        agentClient.stream(request, event -> handleEvent(event, startTime));

        renderer.println();
    }

    private void handleEvent(SseEvent event, long startTime) {
        switch (event.type()) {
            case "token" -> renderer.printToken(event.token());
            case "tool_call" -> renderer.printToolCallInline(event.toolName());
            case "approval_required" -> {
                boolean approved = renderer.promptApproval(event.approvalMessage(), event.dangerous());
                String decision = approved ? "APPROVED" : "DENIED";
                agentClient.resolveApproval(event.requestId(), decision);
                renderer.printMeta("Decision: " + decision);
            }
            case "meta" -> {
                if (showMeta) {
                    renderer.println();
                    renderer.printMeta("--- Response Meta ---");
                    if (event.skillUsed() != null) {
                        renderer.printMeta("  Skill:      " + event.skillUsed());
                    }
                    if (event.routingMethod() != null) {
                        renderer.printMeta("  Routing:    " + event.routingMethod()
                                + " (confidence: " + String.format("%.2f", event.routingConfidence()) + ")");
                    }
                    renderer.printMeta("  Tokens:     " + event.inputTokens() + " in / "
                            + event.outputTokens() + " out");
                    if (showTiming) {
                        long elapsed = System.currentTimeMillis() - startTime;
                        renderer.printMeta("  Duration:   " + elapsed + "ms");
                    }
                }
            }
            case "error" -> renderer.printError(event.errorMessage());
            case "done" -> {
                // Response complete
            }
            default -> {
                // Ignore unknown event types
            }
        }
    }
}
