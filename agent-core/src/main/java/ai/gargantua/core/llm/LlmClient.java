package ai.gargantua.core.llm;

public interface LlmClient {
    LlmResponse chat(LlmRequest request);
}
