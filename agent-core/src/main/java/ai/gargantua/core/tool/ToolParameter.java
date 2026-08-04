package ai.gargantua.core.tool;

/**
 * A single input parameter of a tool, described in provider-neutral terms.
 *
 * <p>This exists so that {@link ToolProvider} implementations can describe their tools
 * without agent-core taking a dependency on any LLM SDK. The engine translates these
 * into whatever schema type the model client expects.</p>
 *
 * <p>{@link #type} uses JSON Schema type names. Providers backed by Java reflection
 * generally emit {@link #TYPE_STRING} and convert at invocation time, while providers
 * backed by a protocol that already carries a schema — such as MCP — pass the declared
 * type through so the model sees the real contract.</p>
 *
 * @param name        parameter name as the model must supply it
 * @param type        JSON Schema type name; see the {@code TYPE_*} constants
 * @param description human-readable purpose, surfaced to the model; may be empty
 * @param required    whether the model must supply this parameter
 *
 * @see ToolDefinition
 */
public record ToolParameter(String name, String type, String description, boolean required) {

    public static final String TYPE_STRING = "string";
    public static final String TYPE_INTEGER = "integer";
    public static final String TYPE_NUMBER = "number";
    public static final String TYPE_BOOLEAN = "boolean";
    public static final String TYPE_OBJECT = "object";
    public static final String TYPE_ARRAY = "array";

    public ToolParameter {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tool parameter name is required");
        }
        type = (type == null || type.isBlank()) ? TYPE_STRING : type;
        description = description == null ? "" : description;
    }

    /** Optional string parameter — the shape produced by reflection-based discovery. */
    public static ToolParameter string(String name) {
        return new ToolParameter(name, TYPE_STRING, "", false);
    }
}
