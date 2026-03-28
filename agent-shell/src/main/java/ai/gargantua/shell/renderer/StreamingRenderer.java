package ai.gargantua.shell.renderer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.PrintStream;
import java.util.Scanner;

@Component
public class StreamingRenderer {

    private final boolean ansiEnabled;
    private final PrintStream out;

    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN = "\u001B[36m";
    private static final String DIM = "\u001B[2m";
    private static final String BOLD = "\u001B[1m";

    public StreamingRenderer(@Value("${agent.shell.ansi:auto}") String ansiMode) {
        this.out = System.out;
        this.ansiEnabled = resolveAnsi(ansiMode);
    }

    StreamingRenderer(boolean ansiEnabled, PrintStream out) {
        this.ansiEnabled = ansiEnabled;
        this.out = out;
    }

    private boolean resolveAnsi(String mode) {
        if ("true".equalsIgnoreCase(mode) || "on".equalsIgnoreCase(mode)) {
            return true;
        }
        if ("false".equalsIgnoreCase(mode) || "off".equalsIgnoreCase(mode)) {
            return false;
        }
        // auto: detect from console
        return System.console() != null;
    }

    public boolean isAnsiEnabled() {
        return ansiEnabled;
    }

    public void printToken(String token) {
        out.print(token);
        out.flush();
    }

    public void printMeta(String meta) {
        if (ansiEnabled) {
            out.println(DIM + meta + RESET);
        } else {
            out.println(meta);
        }
        out.flush();
    }

    public void printToolCallInline(String toolName) {
        if (ansiEnabled) {
            out.println(CYAN + "[" + toolName + "]" + RESET);
        } else {
            out.println("[" + toolName + "]");
        }
        out.flush();
    }

    public void printError(String error) {
        if (ansiEnabled) {
            out.println(RED + "ERROR: " + error + RESET);
        } else {
            out.println("ERROR: " + error);
        }
        out.flush();
    }

    public boolean promptApproval(String message, boolean dangerous) {
        if (ansiEnabled) {
            String color = dangerous ? RED : YELLOW;
            out.print(color + BOLD + "APPROVAL REQUIRED: " + RESET);
            out.println(message);
            out.print(dangerous
                    ? RED + "This action is DANGEROUS. Approve? (y/n): " + RESET
                    : YELLOW + "Approve? (y/n): " + RESET);
        } else {
            out.println("APPROVAL REQUIRED: " + message);
            out.print(dangerous
                    ? "This action is DANGEROUS. Approve? (y/n): "
                    : "Approve? (y/n): ");
        }
        out.flush();

        Scanner scanner = new Scanner(System.in);
        String input = scanner.nextLine().trim().toLowerCase();
        return "y".equals(input) || "yes".equals(input);
    }

    public void println() {
        out.println();
        out.flush();
    }

    public void println(String text) {
        out.println(text);
        out.flush();
    }

    public void printInfo(String info) {
        if (ansiEnabled) {
            out.println(GREEN + info + RESET);
        } else {
            out.println(info);
        }
        out.flush();
    }

    public void printChatHeader(String sessionId, String userId, boolean dryRun) {
        println();
        if (ansiEnabled) {
            out.println(BOLD + CYAN + "=== Agent Shell ===" + RESET);
            out.println(DIM + "Session: " + sessionId + RESET);
            out.println(DIM + "User:    " + userId + RESET);
            if (dryRun) {
                out.println(YELLOW + "[DRY RUN MODE]" + RESET);
            }
            out.println(DIM + "Type \\help for commands, \\exit to quit" + RESET);
        } else {
            out.println("=== Agent Shell ===");
            out.println("Session: " + sessionId);
            out.println("User:    " + userId);
            if (dryRun) {
                out.println("[DRY RUN MODE]");
            }
            out.println("Type \\help for commands, \\exit to quit");
        }
        println();
        out.flush();
    }

    public String stripAnsi(String text) {
        return text.replaceAll("\u001B\\[[;\\d]*m", "");
    }
}
