package io.agentkit.core.memory;

import java.util.List;

public record ComposedMemory(
        List<ChatMessage> workingMessages,
        List<SessionSummary> episodicSummaries,
        List<KnowledgeSegment> knowledgeSegments,
        int estimatedTokens
) {
}
