package ai.gargantua.core.a2a;

import ai.gargantua.core.a2a.AgentCard.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentCard")
class AgentCardTest {

    @Test
    @DisplayName("all top-level fields are accessible via record accessors")
    void allFieldsAccessible() {
        AgentCapabilities caps = new AgentCapabilities(true, false);
        AgentSkill skill = new AgentSkill("sk1", "Greeting", "Greets users", "general", List.of("greeting"));
        AgentProvider provider = new AgentProvider("Acme Corp", "https://acme.com");
        AgentAuthScheme auth = new AgentAuthScheme("bearer", "JWT token");

        AgentCard card = new AgentCard(
                "FitCoach", "AI fitness assistant", "1.0.0",
                "https://fitcoach.example.com", "1.0",
                caps,
                List.of("text/plain"),
                List.of("text/plain"),
                List.of(skill),
                provider,
                List.of(auth)
        );

        assertEquals("FitCoach", card.name());
        assertEquals("AI fitness assistant", card.description());
        assertEquals("1.0.0", card.version());
        assertEquals("https://fitcoach.example.com", card.url());
        assertEquals("1.0", card.protocolVersion());
        assertEquals(caps, card.capabilities());
        assertEquals(List.of("text/plain"), card.defaultInputModes());
        assertEquals(List.of("text/plain"), card.defaultOutputModes());
        assertEquals(1, card.skills().size());
        assertEquals(provider, card.provider());
        assertEquals(1, card.authSchemes().size());
    }

    @Test
    @DisplayName("AgentCapabilities stores streaming and pushNotifications flags")
    void capabilities() {
        AgentCapabilities caps = new AgentCapabilities(true, true);
        assertTrue(caps.streaming());
        assertTrue(caps.pushNotifications());

        AgentCapabilities noCaps = new AgentCapabilities(false, false);
        assertFalse(noCaps.streaming());
        assertFalse(noCaps.pushNotifications());
    }

    @Test
    @DisplayName("AgentSkill stores all fields including domain extension")
    void agentSkill() {
        AgentSkill skill = new AgentSkill("sk1", "Workout", "Creates workouts", "fitness", List.of("exercise", "health"));

        assertEquals("sk1", skill.id());
        assertEquals("Workout", skill.name());
        assertEquals("Creates workouts", skill.description());
        assertEquals("fitness", skill.domain());
        assertEquals(2, skill.tags().size());
    }

    @Test
    @DisplayName("AgentProvider stores organization and url")
    void agentProvider() {
        AgentProvider provider = new AgentProvider("Gargantua AI", "https://gargantua.ai");
        assertEquals("Gargantua AI", provider.organization());
        assertEquals("https://gargantua.ai", provider.url());
    }

    @Test
    @DisplayName("AgentAuthScheme stores scheme and description")
    void authScheme() {
        AgentAuthScheme none = new AgentAuthScheme("none", "No auth required");
        assertEquals("none", none.scheme());

        AgentAuthScheme apiKey = new AgentAuthScheme("apiKey", "API key in header");
        assertEquals("apiKey", apiKey.scheme());
    }

    @Test
    @DisplayName("nullable provider and authSchemes")
    void nullableFields() {
        AgentCard card = new AgentCard(
                "Agent", "desc", "1.0", "http://localhost", "1.0",
                new AgentCapabilities(false, false),
                List.of("text/plain"), List.of("text/plain"),
                List.of(), null, null
        );

        assertNull(card.provider());
        assertNull(card.authSchemes());
    }

    @Test
    @DisplayName("empty skills list")
    void emptySkills() {
        AgentCard card = new AgentCard(
                "Agent", "desc", "1.0", "http://localhost", "1.0",
                new AgentCapabilities(false, false),
                List.of(), List.of(), List.of(), null, null
        );
        assertTrue(card.skills().isEmpty());
    }

    @Test
    @DisplayName("record equality for top-level and nested records")
    void equality() {
        AgentCapabilities caps = new AgentCapabilities(true, false);
        AgentCard a = new AgentCard("A", "d", "1.0", "url", "1.0", caps, List.of(), List.of(), List.of(), null, null);
        AgentCard b = new AgentCard("A", "d", "1.0", "url", "1.0", caps, List.of(), List.of(), List.of(), null, null);
        AgentCard c = new AgentCard("B", "d", "1.0", "url", "1.0", caps, List.of(), List.of(), List.of(), null, null);

        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());

        // nested record equality
        AgentSkill s1 = new AgentSkill("id", "n", "d", "dom", List.of());
        AgentSkill s2 = new AgentSkill("id", "n", "d", "dom", List.of());
        assertEquals(s1, s2);
    }

    @Test
    @DisplayName("multiple input and output modes")
    void multipleModes() {
        AgentCard card = new AgentCard(
                "Agent", "desc", "1.0", "url", "1.0",
                new AgentCapabilities(true, true),
                List.of("text/plain", "application/json"),
                List.of("text/plain", "text/html"),
                List.of(), null, null
        );

        assertEquals(2, card.defaultInputModes().size());
        assertEquals(2, card.defaultOutputModes().size());
        assertEquals("application/json", card.defaultInputModes().get(1));
    }
}
