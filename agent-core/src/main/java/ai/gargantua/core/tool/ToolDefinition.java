package ai.gargantua.core.tool;

/**
 * Runtime descriptor for a discovered agent tool. Built by the {@code ToolRegistry}
 * at boot time by scanning {@link AgentTool}-annotated methods and merging metadata
 * from {@link RequiresApproval} and {@link CacheableToolResult} annotations.
 *
 * @param name                     tool name (from annotation or method name)
 * @param description              LLM-facing description
 * @param parallelizable           whether the tool can run concurrently with other tools
 * @param requiresApproval         whether HITL approval is needed before execution
 * @param cacheable                whether results are cached
 * @param approvalMessage          human-readable message shown in the approval UI
 * @param approvalShowParameters   parameter names to surface in the approval UI
 *                                 (empty = show all). Honoured by the registry's
 *                                 approval gate and by the SSE controller starting
 *                                 in 1.2.6.
 * @param dangerous                whether the tool is flagged as high-risk
 *
 * @see AgentTool
 */
public record ToolDefinition(
        String name,
        String description,
        boolean parallelizable,
        boolean requiresApproval,
        boolean cacheable,
        String approvalMessage,
        String[] approvalShowParameters,
        boolean dangerous
) {
}
