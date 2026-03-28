package ai.gargantua.core.tool;

/**
 * Determines the isolation boundary for cached tool results.
 *
 * @see CacheableToolResult
 */
public enum CacheScope {
    /** Shared across all users and sessions. Best for public/static data (e.g. exchange rates). */
    GLOBAL,
    /** Scoped to a single user across all their sessions. Best for user-specific lookups. */
    USER,
    /** Scoped to a single conversation session. Best for context-dependent results. */
    SESSION
}
