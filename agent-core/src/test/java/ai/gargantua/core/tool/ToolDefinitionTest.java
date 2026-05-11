package ai.gargantua.core.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolDefinition")
class ToolDefinitionTest {

    private static final String[] NO_PARAMS = new String[0];

    @Test
    @DisplayName("all fields are accessible via record accessors")
    void allFieldsAccessible() {
        ToolDefinition tool = new ToolDefinition(
                "searchWeb", "Searches the web for information",
                true, true, true, "This tool will search the internet",
                new String[]{"query"}, true
        );

        assertEquals("searchWeb", tool.name());
        assertEquals("Searches the web for information", tool.description());
        assertTrue(tool.parallelizable());
        assertTrue(tool.requiresApproval());
        assertTrue(tool.cacheable());
        assertEquals("This tool will search the internet", tool.approvalMessage());
        assertArrayEquals(new String[]{"query"}, tool.approvalShowParameters());
        assertTrue(tool.dangerous());
    }

    @Test
    @DisplayName("safe non-approval tool with all flags false")
    void safeTool() {
        ToolDefinition tool = new ToolDefinition(
                "getCurrentTime", "Returns current time",
                false, false, false, null, NO_PARAMS, false
        );

        assertFalse(tool.parallelizable());
        assertFalse(tool.requiresApproval());
        assertFalse(tool.cacheable());
        assertNull(tool.approvalMessage());
        assertEquals(0, tool.approvalShowParameters().length);
        assertFalse(tool.dangerous());
    }

    @Test
    @DisplayName("parallelizable cacheable tool without approval")
    void parallelizableCacheable() {
        ToolDefinition tool = new ToolDefinition(
                "lookup", "Looks up data", true, false, true, null, NO_PARAMS, false
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
                false, true, false, "This will permanently delete the account",
                new String[]{"accountId"}, true
        );

        assertTrue(tool.requiresApproval());
        assertTrue(tool.dangerous());
        assertEquals("This will permanently delete the account", tool.approvalMessage());
        assertArrayEquals(new String[]{"accountId"}, tool.approvalShowParameters());
    }

    @Test
    @DisplayName("record equality based on all fields")
    void equality() {
        ToolDefinition a = new ToolDefinition("t", "d", true, false, true, null, NO_PARAMS, false);
        ToolDefinition b = new ToolDefinition("t", "d", true, false, true, null, NO_PARAMS, false);
        ToolDefinition c = new ToolDefinition("t2", "d", true, false, true, null, NO_PARAMS, false);

        // Note: array equality semantics on records use Arrays.equals at the
        // component level, so two records that hold equal-by-contents arrays
        // are still considered unequal under default record equals. We pass
        // the SAME NO_PARAMS reference to keep this stable.
        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("allows null name and description")
    void nullFields() {
        ToolDefinition tool = new ToolDefinition(null, null, false, false, false, null, NO_PARAMS, false);
        assertNull(tool.name());
        assertNull(tool.description());
    }

    @Test
    @DisplayName("empty string name and description are valid")
    void emptyStrings() {
        ToolDefinition tool = new ToolDefinition("", "", false, false, false, "", NO_PARAMS, false);
        assertEquals("", tool.name());
        assertEquals("", tool.description());
        assertEquals("", tool.approvalMessage());
    }

    @Test
    @DisplayName("toString contains field values")
    void toStringContainsFields() {
        ToolDefinition tool = new ToolDefinition("myTool", "desc", true, false, true, null, NO_PARAMS, false);
        String str = tool.toString();
        assertTrue(str.contains("myTool"));
        assertTrue(str.contains("desc"));
    }
}
