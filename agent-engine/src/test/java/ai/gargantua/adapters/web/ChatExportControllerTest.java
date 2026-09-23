package ai.gargantua.adapters.web;

import ai.gargantua.core.memory.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatExportController")
class ChatExportControllerTest {

    @Mock
    private MongoTemplate mongoTemplate;

    private ChatExportController controller;

    @BeforeEach
    void setUp() {
        controller = new ChatExportController(mongoTemplate);
        when(mongoTemplate.find(any(), eq(ChatMessage.class), anyString()))
                .thenReturn(List.of(new ChatMessage("user", "hi", Instant.parse("2026-01-01T00:00:00Z"))));
    }

    @Test
    @DisplayName("exportUserData respects format=md instead of always returning JSON")
    void exportUserDataRespectsMarkdownFormat() {
        var response = controller.exportUserData("u1", null, null, "md");

        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).isEqualTo(MediaType.TEXT_PLAIN_VALUE);
        assertThat(response.getBody()).startsWith("# Chat Export - User u1");
    }

    @Test
    @DisplayName("exportUserData respects format=txt")
    void exportUserDataRespectsTextFormat() {
        var response = controller.exportUserData("u1", null, null, "txt");

        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).isEqualTo(MediaType.TEXT_PLAIN_VALUE);
        assertThat(response.getBody()).contains("USER: hi");
    }

    @Test
    @DisplayName("exportUserData defaults to JSON")
    void exportUserDataDefaultsToJson() {
        var response = controller.exportUserData("u1", null, null, "json");

        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getBody()).contains("\"role\":\"user\"");
    }
}
