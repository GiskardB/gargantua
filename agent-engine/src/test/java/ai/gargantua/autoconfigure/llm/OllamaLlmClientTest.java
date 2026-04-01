package ai.gargantua.autoconfigure.llm;

import ai.gargantua.core.llm.LlmRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OllamaLlmClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void constructsWithDefaultEndpoint() {
        var client = new OllamaLlmClient(null);
        assertNotNull(client);
    }

    @Test
    void constructsWithCustomEndpoint() {
        var client = new OllamaLlmClient("http://my-ollama:11434");
        assertNotNull(client);
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildRequestBodyContainsExpectedFields() throws Exception {
        var client = new OllamaLlmClient("http://localhost:11434");

        var messages = List.of(
                new LlmRequest.LlmMessage("system", "You are a router."),
                new LlmRequest.LlmMessage("user", "Route this message")
        );
        var request = new LlmRequest("phi4-mini", messages, 0.0, 50);

        String json = client.buildRequestBody(request);
        Map<String, Object> parsed = objectMapper.readValue(json, Map.class);

        assertEquals("phi4-mini", parsed.get("model"));
        assertEquals(false, parsed.get("stream"));

        var parsedMessages = (List<Map<String, Object>>) parsed.get("messages");
        assertEquals(2, parsedMessages.size());
        assertEquals("system", parsedMessages.get(0).get("role"));
        assertEquals("You are a router.", parsedMessages.get(0).get("content"));
        assertEquals("user", parsedMessages.get(1).get("role"));

        var options = (Map<String, Object>) parsed.get("options");
        assertEquals(0.0, ((Number) options.get("temperature")).doubleValue());
        assertEquals(50, ((Number) options.get("num_predict")).intValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildRequestBodyOmitsNumPredictWhenZero() throws Exception {
        var client = new OllamaLlmClient("http://localhost:11434");

        var messages = List.of(new LlmRequest.LlmMessage("user", "Hi"));
        var request = new LlmRequest("llama3", messages, 0.7, 0);

        String json = client.buildRequestBody(request);
        Map<String, Object> parsed = objectMapper.readValue(json, Map.class);

        var options = (Map<String, Object>) parsed.get("options");
        assertFalse(options.containsKey("num_predict"),
                "num_predict should be omitted when maxTokens is 0");
    }
}
