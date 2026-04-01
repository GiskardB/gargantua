package ai.gargantua.core.llm;

public record LlmResponse(
    String content,
    String model,
    int inputTokens,
    int outputTokens
) {}
