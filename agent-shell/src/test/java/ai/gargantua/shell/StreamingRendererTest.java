package ai.gargantua.shell;

import ai.gargantua.shell.renderer.StreamingRenderer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

class StreamingRendererTest {

    @Test
    void printToken_outputsWithoutNewline() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        StreamingRenderer renderer = new StreamingRenderer(false, ps);

        renderer.printToken("hello");
        renderer.printToken(" world");

        assertEquals("hello world", baos.toString());
    }

    @Test
    void printError_noAnsi_plainText() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        StreamingRenderer renderer = new StreamingRenderer(false, ps);

        renderer.printError("something went wrong");

        String output = baos.toString().trim();
        assertEquals("ERROR: something went wrong", output);
        assertFalse(output.contains("\u001B"), "Should not contain ANSI codes when disabled");
    }

    @Test
    void printError_withAnsi_containsAnsiCodes() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        StreamingRenderer renderer = new StreamingRenderer(true, ps);

        renderer.printError("bad things");

        String output = baos.toString();
        assertTrue(output.contains("\u001B[31m"), "Should contain red ANSI code");
        assertTrue(output.contains("\u001B[0m"), "Should contain reset ANSI code");
        assertTrue(output.contains("bad things"));
    }

    @Test
    void printInfo_noAnsi_plainText() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        StreamingRenderer renderer = new StreamingRenderer(false, ps);

        renderer.printInfo("all good");

        String output = baos.toString().trim();
        assertEquals("all good", output);
        assertFalse(output.contains("\u001B"));
    }

    @Test
    void printInfo_withAnsi_containsGreen() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        StreamingRenderer renderer = new StreamingRenderer(true, ps);

        renderer.printInfo("success");

        String output = baos.toString();
        assertTrue(output.contains("\u001B[32m"), "Should contain green ANSI code");
    }

    @Test
    void printToolCallInline_noAnsi() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        StreamingRenderer renderer = new StreamingRenderer(false, ps);

        renderer.printToolCallInline("myTool");

        assertEquals("[myTool]" + System.lineSeparator(), baos.toString());
    }

    @Test
    void printToolCallInline_withAnsi_containsCyan() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        StreamingRenderer renderer = new StreamingRenderer(true, ps);

        renderer.printToolCallInline("myTool");

        String output = baos.toString();
        assertTrue(output.contains("\u001B[36m"));
        assertTrue(output.contains("[myTool]"));
    }

    @Test
    void printMeta_noAnsi_plainText() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        StreamingRenderer renderer = new StreamingRenderer(false, ps);

        renderer.printMeta("metadata info");

        assertEquals("metadata info" + System.lineSeparator(), baos.toString());
    }

    @Test
    void stripAnsi_removesAllEscapeCodes() {
        StreamingRenderer renderer = new StreamingRenderer(true, System.out);

        String input = "\u001B[31mERROR:\u001B[0m something \u001B[32mgreen\u001B[0m";
        String stripped = renderer.stripAnsi(input);

        assertEquals("ERROR: something green", stripped);
        assertFalse(stripped.contains("\u001B"));
    }

    @Test
    void stripAnsi_preservesPlainText() {
        StreamingRenderer renderer = new StreamingRenderer(false, System.out);

        String input = "plain text with no codes";
        assertEquals(input, renderer.stripAnsi(input));
    }

    @Test
    void isAnsiEnabled_reflectsConstructorSetting() {
        StreamingRenderer enabled = new StreamingRenderer(true, System.out);
        StreamingRenderer disabled = new StreamingRenderer(false, System.out);

        assertTrue(enabled.isAnsiEnabled());
        assertFalse(disabled.isAnsiEnabled());
    }

    @Test
    void printChatHeader_noAnsi_containsSessionInfo() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        StreamingRenderer renderer = new StreamingRenderer(false, ps);

        renderer.printChatHeader("test-session-123", "test-user", false);

        String output = baos.toString();
        assertTrue(output.contains("=== Agent Shell ==="));
        assertTrue(output.contains("test-session-123"));
        assertTrue(output.contains("test-user"));
        assertFalse(output.contains("[DRY RUN MODE]"));
    }

    @Test
    void printChatHeader_dryRun_showsDryRunBanner() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        StreamingRenderer renderer = new StreamingRenderer(false, ps);

        renderer.printChatHeader("s1", "u1", true);

        assertTrue(baos.toString().contains("[DRY RUN MODE]"));
    }
}
