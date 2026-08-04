package ai.gargantua.core.tool;

import java.util.List;

/**
 * Runtime descriptor for a discovered agent tool, independent of where the tool came
 * from. Built either by scanning {@link AgentTool}-annotated methods and merging metadata
 * from {@link RequiresApproval} and {@link CacheableToolResult}, or by a
 * {@link ToolProvider} backed by an external source such as an MCP server.
 *
 * @param name             tool name (from annotation or method name)
 * @param description      LLM-facing description
 * @param parallelizable   whether the tool can run concurrently with other tools
 * @param requiresApproval whether HITL approval is needed before execution
 * @param cacheable        whether results are cached
 * @param approvalMessage  human-readable message shown in the approval UI
 * @param dangerous        whether the tool is flagged as high-risk
 * @param parameters       declared input parameters; empty when the tool takes none
 *
 * @see AgentTool
 * @see ToolProvider
 */
public record ToolDefinition(
        String name,
        String description,
        boolean parallelizable,
        boolean requiresApproval,
        boolean cacheable,
        String approvalMessage,
        boolean dangerous,
        List<ToolParameter> parameters
) {

    public ToolDefinition {
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
    }

    /**
     * Creates a descriptor for a tool with no declared parameter schema. Retained so
     * that existing callers built before parameter descriptions were modelled continue
     * to compile unchanged.
     */
    public ToolDefinition(String name, String description, boolean parallelizable,
                          boolean requiresApproval, boolean cacheable,
                          String approvalMessage, boolean dangerous) {
        this(name, description, parallelizable, requiresApproval, cacheable,
                approvalMessage, dangerous, List.of());
    }

    /** Whether this tool declares any input parameters. */
    public boolean hasParameters() {
        return !parameters.isEmpty();
    }
}
