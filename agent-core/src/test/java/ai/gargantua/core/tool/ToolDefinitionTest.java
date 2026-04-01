package ai.gargantua.core.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolDefinition")
class ToolDefinitionTest {

    @Test
    @DisplayName("all fields are accessible via record accessors")
    void allFieldsAccessible() {
        ToolDefinition tool = new ToolDefinition(
                "searchWeb", "Searches the web for information",
                true, true, true, "This tool will search the internet", true
        );

        assertEquals("searchWeb", tool.name());
        assertEquals("Searches the web for information", tool.description());
        assertTrue(tool.parallelizable());
        assertTrue(tool.requiresApproval());
        assertTrue(tool.cacheable());
        assertEquals("This tool will search the internet", tool.approvalMessage());
        assertTrue(tool.dangerous());
    }

    @Test
    @DisplayName("safe non-approval tool with all flags false")
    void safeTool() {
        ToolDefinition tool = new ToolDefinition(
                "getCurrentTime", "Returns current time",
                false, false, false, null, false
        );

        assertFalse(tool.parallelizable());
        assertFalse(tool.requiresApproval());
        assertFalse(tool.cacheable());
        assertNull(tool.approvalMessage());
        assertFalse(tool.dangerous());
    }

    @Test
    @DisplayName("parallelizable cacheable tool without approval")
    void parallelizableCacheable() {
        ToolDefinition tool = new ToolDefinition(
                "lookup", "Looks up data", true, false, true, null, false
        );

        assertTrue(tool.parallelizable());
        assertFalse(tool.requiresApproval());
        assertTrue(tool.cacheable());
    }

    @Test
    @DisplayName("dangerous tool requiring approval")
    void dangerousTool() {
        ToolDefinition tool = new ToolDefinition(
                "deleteAccount", "Deletes user account",
                false, true, false, "This will permanently delete the account", true
        );

        assertTrue(tool.requiresApproval());
        assertTrue(tool.dangerous());
        assertEquals("This will permanently delete the account", tool.approvalMessage());
    }

    @Test
    @DisplayName("record equality based on all fields")
    void equality() {
        ToolDefinition a = new ToolDefinition("t", "d", true, false, true, null, false);
        ToolDefinition b = new ToolDefinition("t", "d", true, false, true, null, false);
        ToolDefinition c = new ToolDefinition("t2", "d", true, false, true, null, false);

        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("allows null name and description")
    void nullFields() {
        ToolDefinition tool = new ToolDefinition(null, null, false, false, false, null, false);
        assertNull(tool.name());
        assertNull(tool.description());
    }

    @Test
    @DisplayName("empty string name and description are valid")
    void emptyStrings() {
        ToolDefinition tool = new ToolDefinition("", "", false, false, false, "", false);
        assertEquals("", tool.name());
        assertEquals("", tool.description());
        assertEquals("", tool.approvalMessage());
    }

    @Test
    @DisplayName("toString contains field values")
    void toStringContainsFields() {
        ToolDefinition tool = new ToolDefinition("myTool", "desc", true, false, true, null, false);
        String str = tool.toString();
        assertTrue(str.contains("myTool"));
        assertTrue(str.contains("desc"));
    }
}
