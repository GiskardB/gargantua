package ai.gargantua.adapters.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SessionController")
class SessionControllerTest {

    private SessionController controller;

    @BeforeEach
    void setUp() {
        controller = new SessionController();
    }

    @Test
    @DisplayName("newSession returns 200 with sessionId")
    void newSessionReturns200WithSessionId() {
        var response = controller.newSession();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("sessionId");
    }

    @Test
    @DisplayName("newSession generates sessionId with sess_ prefix")
    void sessionIdHasSessPrefix() {
        var response = controller.newSession();

        String sessionId = response.getBody().get("sessionId");
        assertThat(sessionId).startsWith("sess_");
    }

    @Test
    @DisplayName("newSession generates unique sessionIds on each call")
    void generatesUniqueSessionIds() {
        var response1 = controller.newSession();
        var response2 = controller.newSession();

        String id1 = response1.getBody().get("sessionId");
        String id2 = response2.getBody().get("sessionId");

        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    @DisplayName("newSession response body contains exactly one entry")
    void responseContainsExactlyOneEntry() {
        var response = controller.newSession();

        Map<String, String> body = response.getBody();
        assertThat(body).hasSize(1);
    }

    @Test
    @DisplayName("sessionId follows UUID format after prefix")
    void sessionIdContainsUuid() {
        var response = controller.newSession();
        String sessionId = response.getBody().get("sessionId");

        // Remove "sess_" prefix and validate UUID format
        String uuidPart = sessionId.substring("sess_".length());
        assertThat(uuidPart).matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }
}
