package ai.gargantua.core.execution;

/**
 * The kind of a single {@link ExecutionEvent} in an agent's execution timeline.
 *
 * <p>Where an {@link ai.gargantua.core.audit.AuditEvent} is one <em>summary</em> row
 * written after a request, execution events are the <em>fine-grained stream</em> emitted
 * <em>during</em> it — the step-by-step trace the Studio renders and observability tools
 * consume. The set follows the pipeline an agent turn actually goes through:
 * input → routing → guardrails → model/tools/memory → output.</p>
 */
public enum ExecutionEventType {

    /** A new turn/request began. Typically the first event of a trace. */
    TURN_STARTED,

    /** Routing chose (or failed to choose) a skill; details carry method and confidence. */
    ROUTING_DECIDED,

    /** A skill was selected to handle the turn. */
    SKILL_SELECTED,

    /** A guardrail evaluated input or output; details carry the verdict and reason. */
    GUARDRAIL_EVALUATED,

    /** A call to an LLM (any phase: main, routing, summarizer). */
    LLM_CALL,

    /** A tool invocation started; details carry the tool name and arguments. */
    TOOL_CALLED,

    /** A tool invocation returned (or errored); details carry the outcome. */
    TOOL_RESULT,

    /** A memory layer was read from. */
    MEMORY_READ,

    /** A memory layer was written to. */
    MEMORY_WRITE,

    /** Control was handed off to another agent (A2A) or skill. */
    HANDOFF,

    /** The turn completed and produced a final response. Typically the last event. */
    TURN_COMPLETED,

    /** An error occurred at some step; details carry the message. */
    ERROR
}
