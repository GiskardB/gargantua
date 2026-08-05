package ai.gargantua.core.tool;

import java.util.List;

/**
 * Runtime descriptor for a discovered agent tool, independent of where the tool came
 * from. Built either by scanning {@link AgentTool}-annotated methods and merging metadata
 * from {@link RequiresApproval} and {@link CacheableToolResult}, or by a
 * {@link ToolProvider} backed by an external source such as an MCP server.
 *
 * @param name                   tool name (from annotation or method name)
 * @param description            LLM-facing description
 * @param parallelizable         whether the tool can run concurrently with other tools
 * @param requiresApproval       whether HITL approval is needed before execution
 * @param cacheable              whether results are cached
 * @param approvalMessage        human-readable message shown in the approval UI
 * @param approvalShowParameters parameter names to surface in the approval UI
 *                               (empty = show all). Honoured by the registry's approval
 *                               gate and by the SSE controller starting in 1.2.6.
 * @param dangerous              whether the tool is flagged as high-risk
 * @param parameters             declared input parameters; empty when the tool takes none
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
        String[] approvalShowParameters,
        boolean dangerous,
        List<ToolParameter> parameters
) {

    public ToolDefinition {
        // Deliberately not cloned: record equality compares array components by
        // identity, and callers rely on passing a shared empty array so that two
        // otherwise-equal descriptors compare equal. Cloning would silently break that.
        approvalShowParameters = approvalShowParameters == null
                ? new String[0] : approvalShowParameters;
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
    }

    /**
     * Creates a descriptor without a declared parameter schema — the shape used before
     * parameters were modelled, retained so existing callers compile unchanged.
     */
    public ToolDefinition(String name, String description, boolean parallelizable,
                          boolean requiresApproval, boolean cacheable,
                          String approvalMessage, String[] approvalShowParameters,
                          boolean dangerous) {
        this(name, description, parallelizable, requiresApproval, cacheable,
                approvalMessage, approvalShowParameters, dangerous, List.of());
    }

    /**
     * Creates a descriptor with neither approval parameter filtering nor a declared
     * parameter schema. Retained for callers predating both additions.
     */
    public ToolDefinition(String name, String description, boolean parallelizable,
                          boolean requiresApproval, boolean cacheable,
                          String approvalMessage, boolean dangerous) {
        this(name, description, parallelizable, requiresApproval, cacheable,
                approvalMessage, new String[0], dangerous, List.of());
    }

    /** Whether this tool declares any input parameters. */
    public boolean hasParameters() {
        return !parameters.isEmpty();
    }
}
