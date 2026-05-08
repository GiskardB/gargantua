package ai.gargantua.autoconfigure;

import ai.gargantua.core.memory.SessionSummary;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoutingModelSessionSummarizer")
class RoutingModelSessionSummarizerTest {

    @Mock
    private LlmProviderFactory llmProviderFactory;

    @Mock
    private ChatModel routingModel;

    private RoutingModelSessionSummarizer summarizer;

    @BeforeEach
    void setUp() {
        lenient().when(llmProviderFactory.getRoutingModel()).thenReturn(routingModel);
        summarizer = new RoutingModelSessionSummarizer(llmProviderFactory);
    }

    private ai.gargantua.core.memory.ChatMessage userMsg(String text) {
        return new ai.gargantua.core.memory.ChatMessage("user", text, Instant.now());
    }

    private ai.gargantua.core.memory.ChatMessage assistantMsg(String text) {
        return new ai.gargantua.core.memory.ChatMessage("assistant", text, Instant.now());
    }

    private void mockRoutingModelResponse(String text) {
        when(routingModel.chat(any(ChatMessage.class), any(ChatMessage.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(text)).build());
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("parses well-formed JSON from the routing model")
        void parsesJson() {
            mockRoutingModelResponse("""
                    {"summary":"Discussed Java records and Spring Boot 3 migration",
                     "keyTopics":["Java records","Spring Boot 3"],
                     "unresolvedItems":["Decide on Mongo TTL index"]}
                    """);

            SessionSummary out = summarizer.summarize("u1", "s1",
                    List.of(userMsg("how do records work"),
                            assistantMsg("they're immutable data classes")));

            assertThat(out.summary()).contains("Java records");
            assertThat(out.keyTopics()).containsExactly("Java records", "Spring Boot 3");
            assertThat(out.unresolvedItems()).containsExactly("Decide on Mongo TTL index");
            assertThat(out.messageCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("strips ```json ... ``` code fences before parsing")
        void stripsCodeFences() {
            mockRoutingModelResponse("""
                    ```json
                    {"summary":"x","keyTopics":["a"],"unresolvedItems":[]}
                    ```
                    """);

            SessionSummary out = summarizer.summarize("u1", "s1", List.of(userMsg("hi")));

            assertThat(out.summary()).isEqualTo("x");
            assertThat(out.keyTopics()).containsExactly("a");
        }

        @Test
        @DisplayName("caps keyTopics and unresolvedItems at 5")
        void capsLists() {
            mockRoutingModelResponse("""
                    {"summary":"y",
                     "keyTopics":["a","b","c","d","e","f","g"],
                     "unresolvedItems":["1","2","3","4","5","6"]}
                    """);

            SessionSummary out = summarizer.summarize("u1", "s1", List.of(userMsg("hi")));

            assertThat(out.keyTopics()).hasSize(5);
            assertThat(out.unresolvedItems()).hasSize(5);
        }
    }

    @Nested
    @DisplayName("fallback behaviour")
    class Fallback {

        @Test
        @DisplayName("returns empty summary when message list is empty (no LLM call)")
        void emptyMessages() {
            SessionSummary out = summarizer.summarize("u1", "s1", List.of());

            assertThat(out.summary()).isEmpty();
            assertThat(out.keyTopics()).isEmpty();
            assertThat(out.messageCount()).isZero();
        }

        @Test
        @DisplayName("falls back to deterministic concat when routing model throws")
        void modelThrows() {
            when(routingModel.chat(any(ChatMessage.class), any(ChatMessage.class)))
                    .thenThrow(new RuntimeException("Ollama unavailable"));

            SessionSummary out = summarizer.summarize("u1", "s1",
                    List.of(userMsg("ping"), assistantMsg("pong")));

            assertThat(out.summary()).contains("user: ping").contains("assistant: pong");
            assertThat(out.keyTopics()).isEmpty();
            assertThat(out.messageCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("treats malformed JSON as the whole summary text")
        void malformedJson() {
            mockRoutingModelResponse("This is just prose, not JSON.");

            SessionSummary out = summarizer.summarize("u1", "s1", List.of(userMsg("hi")));

            assertThat(out.summary()).isEqualTo("This is just prose, not JSON.");
            assertThat(out.keyTopics()).isEmpty();
            assertThat(out.messageCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("ignores empty / null entries inside topic arrays")
        void ignoresEmptyEntries() {
            mockRoutingModelResponse("""
                    {"summary":"x","keyTopics":["a","","b",null,"c"],"unresolvedItems":[]}
                    """);

            SessionSummary out = summarizer.summarize("u1", "s1", List.of(userMsg("hi")));

            assertThat(out.keyTopics()).containsExactly("a", "b", "c");
        }
    }
}
