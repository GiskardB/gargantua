package ai.gargantua.example.cookbook.tools;

import ai.gargantua.core.security.RequiresRole;
import ai.gargantua.core.tool.AgentTool;
import ai.gargantua.core.tool.RequiresApproval;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Administrative tools — restricted via {@code @RequiresRole} to {@code cookbook-admin}
 * (or {@code super-admin}, which Gargantua treats as a master role). Demonstrates the
 * RBAC + HITL combination: dangerous admin actions both need a role AND require
 * explicit human approval.
 */
@Component
public class AdminTool {

    /** A simplified audit log entry surfaced to the admin. */
    public record AuditEntry(String userId, String action, Instant when) {}

    @AgentTool(description = """
            Returns the most recent audit entries for the given user. Visible only
            to users with the cookbook-admin role.
            """)
    @RequiresRole({"cookbook-admin", "super-admin"})
    public List<AuditEntry> viewAuditLog(String userId) {
        // In a real agent, this would query AuditEventStore. We return a small mock.
        return List.of(
                new AuditEntry(userId, "added pancetta to pantry", Instant.now().minusSeconds(3600)),
                new AuditEntry(userId, "generated recipe: Spaghetti aglio e olio", Instant.now().minusSeconds(1800)),
                new AuditEntry(userId, "removed eggs from pantry", Instant.now().minusSeconds(900))
        );
    }

    @AgentTool(description = """
            Permanently purges all stored data (pantry, history, audit, knowledge segments)
            for the given user. Irreversible — only available to admins, and gated by
            human approval.
            """)
    @RequiresRole({"cookbook-admin", "super-admin"})
    @RequiresApproval(
            message = "Purge ALL data for this user? This cannot be undone.",
            showParameters = {"userId"},
            dangerous = true
    )
    public boolean purgeUserData(String userId) {
        // In a real agent, this would delete from MongoDB collections.
        return true;
    }
}
