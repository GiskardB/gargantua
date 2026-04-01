package ai.gargantua.core.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChatMessage")
class ChatMessageTest {

    @Test
    @DisplayName("userMessage factory sets role to 'user' and preserves content")
    void userMessageFactory() {
        Instant before = Instant.now();
        ChatMessage msg = ChatMessage.userMessage("Hello");
        Instant after = Instant.now();

        assertEquals("user", msg.role());
        assertEquals("Hello", msg.content());
        assertNotNull(msg.timestamp());
        assertFalse(msg.timestamp().isBefore(before));
        assertFalse(msg.timestamp().isAfter(after));
    }

    @Test
    @DisplayName("assistantMessage factory sets role to 'assistant' and preserves content")
    void assistantMessageFactory() {
        Instant before = Instant.now();
        ChatMessage msg = ChatMessage.assistantMessage("Hi there");
        Instant after = Instant.now();

        assertEquals("assistant", msg.role());
        assertEquals("Hi there", msg.content());
        assertNotNull(msg.timestamp());
        assertFalse(msg.timestamp().isBefore(before));
        assertFalse(msg.timestamp().isAfter(after));
    }

    @Test
    @DisplayName("canonical constructor accepts arbitrary role")
    void canonicalConstructor() {
        Instant ts = Instant.parse("2024-01-01T00:00:00Z");
        ChatMessage msg = new ChatMessage("system", "You are a bot", ts);

        assertEquals("system", msg.role());
        assertEquals("You are a bot", msg.content());
        assertEquals(ts, msg.timestamp());
    }

    @Test
    @DisplayName("record equality is based on all fields")
    void recordEquality() {
        Instant ts = Instant.parse("2024-06-15T12:00:00Z");
        ChatMessage a = new ChatMessage("user", "Hi", ts);
        ChatMessage b = new ChatMessage("user", "Hi", ts);
        ChatMessage c = new ChatMessage("assistant", "Hi", ts);

        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("allows null content and null timestamp via canonical constructor")
    void nullFields() {
        ChatMessage msg = new ChatMessage("user", null, null);
        assertEquals("user", msg.role());
        assertNull(msg.content());
        assertNull(msg.timestamp());
    }

    @Test
    @DisplayName("userMessage with empty string content")
    void emptyContent() {
        ChatMessage msg = ChatMessage.userMessage("");
        assertEquals("", msg.content());
    }

    @Test
    @DisplayName("toString contains all field values")
    void toStringContainsFields() {
        Instant ts = Instant.parse("2024-01-01T00:00:00Z");
        ChatMessage msg = new ChatMessage("user", "hello", ts);
        String str = msg.toString();

        assertTrue(str.contains("user"));
        assertTrue(str.contains("hello"));
        assertTrue(str.contains("2024-01-01"));
    }
}
