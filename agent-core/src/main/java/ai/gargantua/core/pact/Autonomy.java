package ai.gargantua.core.pact;

/**
 * PACT's autonomy scale (PACT §15) — a semantic declaration of how independently an agent
 * may act, not a security control. {@link #EXECUTING}/{@link #AUTONOMOUS} are exactly
 * where PACT (§16) defers to a governance layer for actual stopping authority — in
 * Gargantua, {@code spec.allowedRoles} and {@code spec.guardrails}.
 *
 * <p>The manifest wire format stays the plain integer PACT's own examples use
 * ({@code autonomy: {level: 2}}); {@link #ofLevel(int)} and {@link #level()} convert at
 * the parse/serialize boundary. This enum exists for type safety in Java code, not to
 * change the manifest format — a future PACT card serializer must emit {@link #level()},
 * not {@link #name()}.</p>
 */
public enum Autonomy {

    /** Observes or reports only; takes no independent action. */
    PASSIVE(0),

    /** Supports a human who remains the actor. */
    ASSISTIVE(1),

    /** Proposes actions; a human must approve before they happen. */
    RECOMMENDING(2),

    /** Carries out approved or narrowly scoped actions itself. */
    EXECUTING(3),

    /** Acts and adapts on an ongoing basis without per-action approval. */
    AUTONOMOUS(4);

    private final int level;

    Autonomy(int level) {
        this.level = level;
    }

    /** The plain 0-4 level PACT's wire format uses. */
    public int level() {
        return level;
    }

    /** Resolves the wire-format integer level (0-4) to its enum constant. */
    public static Autonomy ofLevel(int level) {
        for (Autonomy autonomy : values()) {
            if (autonomy.level == level) {
                return autonomy;
            }
        }
        throw new IllegalArgumentException("Contract autonomy level must be between 0 and 4, got " + level);
    }
}
