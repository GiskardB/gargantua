package ai.gargantua.memory.adapters.inmemory;

import ai.gargantua.core.memory.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryWorkingMemoryAdapterTest {

    private static final String SESSION = "session-1";
    private static final String SESSION_2 = "session-2";

    private InMemoryWorkingMemoryAdapter adapter;

    @BeforeEach
    void setUp() {
        // maxMessages=5, ttl=10 seconds
        adapter = new InMemoryWorkingMemoryAdapter(5, 10_000L);
    }

    // ── getMessages ─────────────────────────────────────────

    @Test
    @DisplayName("getMessages returns empty list for unknown session")
    void getMessages_unknownSession_returnsEmpty() {
        List<ChatMessage> result = adapter.getMessages("nonexistent");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getMessages returns empty list for cleared session")
    void getMessages_clearedSession_returnsEmpty() {
        adapter.appendMessage(SESSION, ChatMessage.userMessage("hi"));
        adapter.clear(SESSION);

        assertThat(adapter.getMessages(SESSION)).isEmpty();
    }

    @Test
    @DisplayName("getMessages returns appended messages in order")
    void getMessages_withMessages_returnsInOrder() {
        adapter.appendMessage(SESSION, ChatMessage.userMessage("first"));
        adapter.appendMessage(SESSION, ChatMessage.assistantMessage("second"));
        adapter.appendMessage(SESSION, ChatMessage.userMessage("third"));

        List<ChatMessage> messages = adapter.getMessages(SESSION);

        assertThat(messages).hasSize(3);
        assertThat(messages.get(0).content()).isEqualTo("first");
        assertThat(messages.get(1).content()).isEqualTo("second");
        assertThat(messages.get(2).content()).isEqualTo("third");
    }

    @Test
    @DisplayName("getMessages returns immutable list")
    void getMessages_returnsImmutableList() {
        adapter.appendMessage(SESSION, ChatMessage.userMessage("hello"));

        List<ChatMessage> messages = adapter.getMessages(SESSION);

        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> messages.add(ChatMessage.userMessage("intruder")));
    }

    // ── appendMessage ───────────────────────────────────────

    @Test
    @DisplayName("appendMessage stores message for the given session")
    void appendMessage_storesMessage() {
        ChatMessage msg = ChatMessage.userMessage("hello");
        adapter.appendMessage(SESSION, msg);

        List<ChatMessage> messages = adapter.getMessages(SESSION);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).role()).isEqualTo("user");
        assertThat(messages.get(0).content()).isEqualTo("hello");
    }

    @Test
    @DisplayName("appendMessage isolates sessions from each other")
    void appendMessage_sessionIsolation() {
        adapter.appendMessage(SESSION, ChatMessage.userMessage("s1-msg"));
        adapter.appendMessage(SESSION_2, ChatMessage.userMessage("s2-msg"));

        assertThat(adapter.getMessages(SESSION)).hasSize(1);
        assertThat(adapter.getMessages(SESSION_2)).hasSize(1);
        assertThat(adapter.getMessages(SESSION).get(0).content()).isEqualTo("s1-msg");
        assertThat(adapter.getMessages(SESSION_2).get(0).content()).isEqualTo("s2-msg");
    }

    // ── Sliding window (maxMessages) ────────────────────────

    @Test
    @DisplayName("appendMessage trims oldest messages when exceeding maxMessages (sliding window)")
    void appendMessage_exceedsMax_trimsOldest() {
        // maxMessages is 5
        for (int i = 1; i <= 7; i++) {
            adapter.appendMessage(SESSION, ChatMessage.userMessage("msg-" + i));
        }

        List<ChatMessage> messages = adapter.getMessages(SESSION);
        assertThat(messages).hasSize(5);
        // oldest 2 should be trimmed; remaining: msg-3 through msg-7
        assertThat(messages.get(0).content()).isEqualTo("msg-3");
        assertThat(messages.get(4).content()).isEqualTo("msg-7");
    }

    @Test
    @DisplayName("appendMessage keeps exactly maxMessages when at capacity")
    void appendMessage_atCapacity_keepsExactMax() {
        for (int i = 1; i <= 5; i++) {
            adapter.appendMessage(SESSION, ChatMessage.userMessage("msg-" + i));
        }

        assertThat(adapter.getMessages(SESSION)).hasSize(5);

        // Add one more
        adapter.appendMessage(SESSION, ChatMessage.userMessage("msg-6"));

        List<ChatMessage> messages = adapter.getMessages(SESSION);
        assertThat(messages).hasSize(5);
        assertThat(messages.get(0).content()).isEqualTo("msg-2");
        assertThat(messages.get(4).content()).isEqualTo("msg-6");
    }

    // ── clear ───────────────────────────────────────────────

    @Test
    @DisplayName("clear removes all messages and expiry for a session")
    void clear_removesSessionData() {
        adapter.appendMessage(SESSION, ChatMessage.userMessage("hello"));
        assertThat(adapter.getMessages(SESSION)).isNotEmpty();

        adapter.clear(SESSION);

        assertThat(adapter.getMessages(SESSION)).isEmpty();
        assertThat(adapter.isExpired(SESSION)).isTrue();
    }

    @Test
    @DisplayName("clear on nonexistent session does not throw")
    void clear_nonexistentSession_noException() {
        adapter.clear("no-such-session"); // should not throw
    }

    // ── isExpired ───────────────────────────────────────────

    @Test
    @DisplayName("isExpired returns true for unknown session (no expiry record)")
    void isExpired_unknownSession_returnsTrue() {
        assertThat(adapter.isExpired("unknown")).isTrue();
    }

    @Test
    @DisplayName("isExpired returns false for recently appended session")
    void isExpired_recentSession_returnsFalse() {
        adapter.appendMessage(SESSION, ChatMessage.userMessage("alive"));
        assertThat(adapter.isExpired(SESSION)).isFalse();
    }

    @Test
    @DisplayName("isExpired returns true after TTL elapses")
    void isExpired_afterTtl_returnsTrue() {
        // Use a very short TTL
        InMemoryWorkingMemoryAdapter shortTtl = new InMemoryWorkingMemoryAdapter(10, 1L);
        shortTtl.appendMessage(SESSION, ChatMessage.userMessage("ephemeral"));

        // Wait a bit beyond the 1ms TTL
        busyWait(50);

        assertThat(shortTtl.isExpired(SESSION)).isTrue();
    }

    @Test
    @DisplayName("getMessages lazily evicts expired session and returns empty")
    void getMessages_expiredSession_returnsEmptyAndEvicts() {
        InMemoryWorkingMemoryAdapter shortTtl = new InMemoryWorkingMemoryAdapter(10, 1L);
        shortTtl.appendMessage(SESSION, ChatMessage.userMessage("will-expire"));

        busyWait(50);

        List<ChatMessage> messages = shortTtl.getMessages(SESSION);
        assertThat(messages).isEmpty();
    }

    @Test
    @DisplayName("appendMessage resets TTL so active sessions stay alive")
    void appendMessage_resetsTtl() {
        // TTL = 200ms
        InMemoryWorkingMemoryAdapter medTtl = new InMemoryWorkingMemoryAdapter(10, 200L);
        medTtl.appendMessage(SESSION, ChatMessage.userMessage("first"));

        busyWait(100);
        // Append again before expiry to reset TTL
        medTtl.appendMessage(SESSION, ChatMessage.userMessage("second"));

        busyWait(100);
        // Should still be alive because TTL was reset
        assertThat(medTtl.isExpired(SESSION)).isFalse();
        assertThat(medTtl.getMessages(SESSION)).hasSize(2);
    }

    // ── Default constructor ─────────────────────────────────

    @Test
    @DisplayName("default constructor creates adapter with maxMessages=20 and ttl=30min")
    void defaultConstructor_usesDefaults() {
        InMemoryWorkingMemoryAdapter defaultAdapter = new InMemoryWorkingMemoryAdapter();

        // Verify maxMessages=20 by adding 21 messages
        for (int i = 1; i <= 21; i++) {
            defaultAdapter.appendMessage(SESSION, ChatMessage.userMessage("msg-" + i));
        }
        assertThat(defaultAdapter.getMessages(SESSION)).hasSize(20);
        assertThat(defaultAdapter.getMessages(SESSION).get(0).content()).isEqualTo("msg-2");

        // Session should not be expired (30 min TTL)
        assertThat(defaultAdapter.isExpired(SESSION)).isFalse();
    }

    // ── Thread safety ───────────────────────────────────────

    @Test
    @DisplayName("concurrent appends do not lose messages or corrupt state")
    void concurrentAppends_threadSafe() throws InterruptedException {
        int threadCount = 10;
        int messagesPerThread = 20;
        InMemoryWorkingMemoryAdapter concurrentAdapter =
                new InMemoryWorkingMemoryAdapter(threadCount * messagesPerThread, 60_000L);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int m = 0; m < messagesPerThread; m++) {
                        concurrentAdapter.appendMessage(SESSION,
                                ChatMessage.userMessage("t" + threadId + "-m" + m));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        List<ChatMessage> messages = concurrentAdapter.getMessages(SESSION);
        assertThat(messages).hasSize(threadCount * messagesPerThread);
    }

    @Test
    @DisplayName("concurrent appends to different sessions are isolated")
    void concurrentAppends_differentSessions_isolated() throws InterruptedException {
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final String sid = "session-" + t;
            executor.submit(() -> {
                try {
                    for (int m = 0; m < 10; m++) {
                        adapter.appendMessage(sid, ChatMessage.userMessage("msg-" + m));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        for (int t = 0; t < threadCount; t++) {
            // maxMessages=5, so each session should have exactly 5
            assertThat(adapter.getMessages("session-" + t)).hasSize(5);
        }
    }

    // ── Helpers ──────────────────────────────────────────────

    private void busyWait(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
