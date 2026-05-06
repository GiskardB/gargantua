package ai.gargantua.example.cookbook.tools;

import ai.gargantua.core.tool.AgentTool;
import ai.gargantua.core.tool.RequiresApproval;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pantry management — read/write user-scoped pantry inventory.
 * Demonstrates {@code @RequiresApproval} on writes (add/remove) and a
 * {@code dangerous=true} flag on the destructive {@code removeFromPantry}.
 *
 * <p>State is held in an in-memory map keyed by user id; production agents
 * would back this with a real persistence layer.</p>
 */
@Component
public class PantryTool {

    /** A pantry entry: ingredient, quantity, unit, and an optional best-before tag. */
    public record PantryItem(String ingredient, double amount, String unit, String bestBefore) {}

    private final Map<String, List<PantryItem>> pantry = new ConcurrentHashMap<>();

    @AgentTool(description = """
            Returns the current pantry contents for the given user.
            Use when the user asks "what do I have", "what's in my pantry", or
            before suggesting recipes that depend on already-owned ingredients.
            """)
    public List<PantryItem> getPantry(String userId) {
        return List.copyOf(pantry.getOrDefault(userId, List.of()));
    }

    @AgentTool(description = """
            Adds an ingredient to the user's pantry. Requires explicit user approval
            because it persists across sessions. Use only when the user clearly
            says "add X to my pantry" or "I just bought N of Y".
            """)
    @RequiresApproval(
            message = "Add this item to your pantry?",
            showParameters = {"userId", "ingredient", "amount", "unit"}
    )
    public PantryItem addToPantry(String userId, String ingredient, double amount, String unit) {
        var item = new PantryItem(ingredient, amount, unit, null);
        pantry.computeIfAbsent(userId, k -> new ArrayList<>()).add(item);
        return item;
    }

    @AgentTool(description = """
            Removes the first matching ingredient from the user's pantry by name.
            Requires approval; flagged as dangerous because the action is irreversible.
            """)
    @RequiresApproval(
            message = "Remove this item from your pantry?",
            showParameters = {"userId", "ingredient"},
            dangerous = true
    )
    public boolean removeFromPantry(String userId, String ingredient) {
        var list = pantry.get(userId);
        if (list == null || list.isEmpty()) return false;
        return list.removeIf(p -> p.ingredient().equalsIgnoreCase(ingredient));
    }
}
