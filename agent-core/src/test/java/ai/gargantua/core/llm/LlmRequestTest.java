package ai.gargantua.core.llm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LlmRequestTest {

    @Test
    void constructsRequestWithAllFields() {
        var messages = List.of(
                new LlmRequest.LlmMessage("system", "You are helpful."),
                new LlmRequest.LlmMessage("user", "Hello")
        );

        var request = new LlmRequest("gpt-4o", messages, 0.7, 1000);

        assertEquals("gpt-4o", request.model());
        assertEquals(2, request.messages().size());
        assertEquals(0.7, request.temperature());
        assertEquals(1000, request.maxTokens());
    }

    @Test
    void messageRecordHoldsRoleAndContent() {
        var message = new LlmRequest.LlmMessage("user", "What is Java?");

        assertEquals("user", message.role());
        assertEquals("What is Java?", message.content());
    }

    @Test
    void responseRecordHoldsAllFields() {
        var response = new LlmResponse("Hello!", "gpt-4o-2024-05-13", 10, 5);

        assertEquals("Hello!", response.content());
        assertEquals("gpt-4o-2024-05-13", response.model());
        assertEquals(10, response.inputTokens());
        assertEquals(5, response.outputTokens());
    }

    @Test
    void providerEnumContainsExpectedValues() {
        assertEquals(4, LlmProvider.values().length);
        assertNotNull(LlmProvider.OPENAI);
        assertNotNull(LlmProvider.ANTHROPIC);
        assertNotNull(LlmProvider.AZURE_OPENAI);
        assertNotNull(LlmProvider.OLLAMA);
    }
}
