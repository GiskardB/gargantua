package ai.gargantua.core.workload;

/**
 * The type of AI workload a manifest describes.
 *
 * <p>{@link #AGENT} is the first and currently the only kind a runtime can execute.
 * The remaining values are reserved so that the manifest schema does not have to change
 * as the platform grows — a Control Plane can already store and route them.</p>
 *
 * @see WorkloadManifest
 */
public enum WorkloadKind {

    /** Conversational agent: skills, tools, memory, guardrails. Executable today. */
    AGENT,

    /** Multi-step pipeline chaining several skills or agents. Reserved. */
    WORKFLOW,

    /** Scores the output of another workload against a dataset. Reserved. */
    EVALUATOR,

    /** Single-shot labelling workload with a fixed output schema. Reserved. */
    CLASSIFIER,

    /** Stateless request/response AI endpoint without conversational memory. Reserved. */
    SERVICE,

    /** Long-running, non-interactive batch job. Reserved. */
    BATCH_JOB
}
