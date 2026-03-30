package ai.gargantua.example.tools;

import ai.gargantua.core.security.RequiresRole;
import ai.gargantua.core.tool.AgentTool;
import ai.gargantua.core.tool.RequiresApproval;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Admin-only tools for managing user fitness profiles.
 * Demonstrates {@code @RequiresRole} for RBAC and {@code @RequiresApproval}
 * with {@code dangerous = true} for destructive operations.
 */
@Component
public class ProfileTool {

    /** A user's fitness profile with goals and restrictions. */
    public record UserProfile(String userId, String name, int age, String fitnessLevel, String goal, List<String> restrictions) {}

    @AgentTool(description = """
            Retrieves a user's fitness profile including their goals, fitness level, and restrictions.
            Admin-only — requires the 'fitness-admin' role.
            """)
    @RequiresRole("fitness-admin")
    public UserProfile getProfile(String userId) {
        return switch (userId.toLowerCase()) {
            case "user-001" -> new UserProfile(
                    "user-001", "Alex Johnson", 28, "intermediate", "muscle gain",
                    List.of());
            case "user-002" -> new UserProfile(
                    "user-002", "Sarah Chen", 34, "advanced", "fat loss",
                    List.of("knee injury — no heavy squats"));
            case "user-003" -> new UserProfile(
                    "user-003", "Mike Torres", 45, "beginner", "general fitness",
                    List.of("lactose intolerant", "lower back pain"));
            default -> new UserProfile(
                    userId, "Demo User", 30, "intermediate", "general fitness",
                    List.of());
        };
    }

    @AgentTool(description = """
            Deletes a user's fitness profile and all associated data.
            Admin-only, dangerous, requires explicit approval.
            """)
    @RequiresRole("fitness-admin")
    @RequiresApproval(
            message = "Permanently delete user profile? This action cannot be undone.",
            showParameters = {"userId"},
            dangerous = true
    )
    public String deleteProfile(String userId) {
        return "Profile deleted for user: " + userId;
    }
}
