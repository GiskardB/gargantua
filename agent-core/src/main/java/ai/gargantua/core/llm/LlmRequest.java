package ai.gargantua.core.llm;

import java.util.List;

public record LlmRequest(
    String model,
    List<LlmMessage> messages,
    double temperature,
    int maxTokens
) {
    public record LlmMessage(String role, String content) {}
}
