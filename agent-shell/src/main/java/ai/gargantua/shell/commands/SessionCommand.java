package ai.gargantua.shell.commands;

import ai.gargantua.shell.client.AgentClient;
import ai.gargantua.shell.renderer.StreamingRenderer;
import ai.gargantua.shell.renderer.TableRenderer;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shell commands for session management. Usage: {@code session new}, {@code session list},
 * {@code session show <id>}. Tracks active sessions in the shell's local state.
 */
@Component
public class SessionCommand {

    private final AgentClient agentClient;
    private final StreamingRenderer renderer;
    private final TableRenderer tableRenderer;

    // Simple in-memory session tracking for the shell
    private final Map<String, SessionInfo> sessions = new LinkedHashMap<>();
    private String activeSessionId;

    private record SessionInfo(String sessionId, String createdAt, int messageCount) {}

    public SessionCommand(AgentClient agentClient,
                          StreamingRenderer renderer,
                          TableRenderer tableRenderer) {
        this.agentClient = agentClient;
        this.renderer = renderer;
        this.tableRenderer = tableRenderer;
    }

    @Command(name = {"session", "new"}, description = "Create a new chat session")
    public String newSession() {
        var sessionId = agentClient.newSession();
        var timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        sessions.put(sessionId, new SessionInfo(sessionId, timestamp, 0));
        activeSessionId = sessionId;
        return "New session created: " + sessionId;
    }

    @Command(name = {"session", "list"}, description = "List all sessions")
    public String list() {
        if (sessions.isEmpty()) {
            return "No sessions. Use 'session new' to create one.";
        }

        var headers = List.of("Session ID", "Created", "Messages", "Active");
        var rows = new ArrayList<List<String>>();

        for (SessionInfo info : sessions.values()) {
            rows.add(List.of(
                    info.sessionId(),
                    info.createdAt(),
                    String.valueOf(info.messageCount()),
                    info.sessionId().equals(activeSessionId) ? "*" : ""
            ));
        }

        return tableRenderer.renderTable(headers, rows);
    }

    @Command(name = {"session", "resume"}, description = "Resume an existing session by ID")
    public String resume(String sessionId) {
        if (!sessions.containsKey(sessionId)) {
            // Allow resuming sessions that weren't tracked locally
            var timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            sessions.put(sessionId, new SessionInfo(sessionId, timestamp, 0));
        }
        activeSessionId = sessionId;
        return "Resumed session: " + sessionId;
    }

    public String getActiveSessionId() {
        return activeSessionId;
    }
}
