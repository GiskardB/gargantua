package ai.gargantua.core.session;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DryRunContextTest {

    @Test
    void inactiveContextHasEmptyStubs() {
        DryRunContext ctx = DryRunContext.inactive();

        assertFalse(ctx.active());
        assertTrue(ctx.toolStubs().isEmpty());
    }

    @Test
    void activeContextPropagatesStubs() {
        Map<String, Object> stubs = Map.of("k", "v");
        DryRunContext ctx = DryRunContext.active(stubs);

        assertTrue(ctx.active());
        assertEquals(1, ctx.toolStubs().size());
        assertEquals("v", ctx.toolStubs().get("k"));
    }
}
