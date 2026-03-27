package io.agentkit.core.orchestrator;

public interface ContextEnricher {

    String sectionName();

    int order();

    default String targetSkill() {
        return null;
    }

    String enrich(EnricherContext ctx);
}
