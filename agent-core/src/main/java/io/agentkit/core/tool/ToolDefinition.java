package io.agentkit.core.tool;

public record ToolDefinition(
        String name,
        String description,
        boolean parallelizable,
        boolean requiresApproval,
        boolean cacheable,
        String approvalMessage,
        boolean dangerous
) {
}
