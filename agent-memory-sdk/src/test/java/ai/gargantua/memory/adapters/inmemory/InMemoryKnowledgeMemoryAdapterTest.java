package ai.gargantua.memory.adapters.inmemory;

import ai.gargantua.core.memory.KnowledgeSegment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryKnowledgeMemoryAdapterTest {

    private static final String USER = "user-1";
    private static final String USER_2 = "user-2";

    private InMemoryKnowledgeMemoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new InMemoryKnowledgeMemoryAdapter();
    }

    // ── getSegments ─────────────────────────────────────────

    @Test
    @DisplayName("getSegments returns empty list for unknown user")
    void getSegments_unknownUser_returnsEmpty() {
        assertThat(adapter.getSegments("nonexistent")).isEmpty();
    }

    @Test
    @DisplayName("getSegments returns empty list when no segments exist")
    void getSegments_noSegments_returnsEmpty() {
        assertThat(adapter.getSegments(USER)).isEmpty();
    }

    @Test
    @DisplayName("getSegments returns all segments for a user")
    void getSegments_withSegments_returnsAll() {
        adapter.upsertSegment(USER, "prefs", "Likes dark mode");
        adapter.upsertSegment(USER, "profile", "Works in finance");

        List<KnowledgeSegment> segments = adapter.getSegments(USER);

        assertThat(segments).hasSize(2);
        assertThat(segments).extracting(KnowledgeSegment::segmentKey)
                .containsExactlyInAnyOrder("prefs", "profile");
    }

    @Test
    @DisplayName("getSegments returns immutable list")
    void getSegments_returnsImmutableList() {
        adapter.upsertSegment(USER, "key", "value");

        List<KnowledgeSegment> segments = adapter.getSegments(USER);

        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> segments.add(new ai.gargantua.core.memory.KnowledgeSegment(
                        USER, "hack", "bad", java.time.Instant.now(), "test")));
    }

    @Test
    @DisplayName("getSegments isolates users from each other")
    void getSegments_userIsolation() {
        adapter.upsertSegment(USER, "key1", "user1-data");
        adapter.upsertSegment(USER_2, "key1", "user2-data");

        List<KnowledgeSegment> u1 = adapter.getSegments(USER);
        List<KnowledgeSegment> u2 = adapter.getSegments(USER_2);

        assertThat(u1).hasSize(1);
        assertThat(u2).hasSize(1);
        assertThat(u1.get(0).content()).isEqualTo("user1-data");
        assertThat(u2.get(0).content()).isEqualTo("user2-data");
    }

    // ── upsertSegment (insert) ──────────────────────────────

    @Test
    @DisplayName("upsertSegment inserts a new segment")
    void upsertSegment_insert_newSegment() {
        adapter.upsertSegment(USER, "prefs", "Likes dark mode");

        List<KnowledgeSegment> segments = adapter.getSegments(USER);
        assertThat(segments).hasSize(1);

        KnowledgeSegment seg = segments.get(0);
        assertThat(seg.userId()).isEqualTo(USER);
        assertThat(seg.segmentKey()).isEqualTo("prefs");
        assertThat(seg.content()).isEqualTo("Likes dark mode");
        assertThat(seg.source()).isEqualTo("embedded");
        assertThat(seg.updatedAt()).isNotNull();
    }

    // ── upsertSegment (update) ──────────────────────────────

    @Test
    @DisplayName("upsertSegment with same key updates content (upsert semantics)")
    void upsertSegment_update_replacesContent() {
        adapter.upsertSegment(USER, "prefs", "Likes dark mode");
        adapter.upsertSegment(USER, "prefs", "Prefers light mode now");

        List<KnowledgeSegment> segments = adapter.getSegments(USER);
        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).content()).isEqualTo("Prefers light mode now");
    }

    @Test
    @DisplayName("upsertSegment with same key preserves segment key uniqueness per user")
    void upsertSegment_keyUniquenessPerUser() {
        adapter.upsertSegment(USER, "prefs", "v1");
        adapter.upsertSegment(USER, "prefs", "v2");
        adapter.upsertSegment(USER, "prefs", "v3");

        // Should always have exactly 1 segment with key "prefs"
        assertThat(adapter.getSegments(USER)).hasSize(1);
        assertThat(adapter.getSegments(USER).get(0).content()).isEqualTo("v3");
    }

    @Test
    @DisplayName("upsertSegment same key for different users creates separate segments")
    void upsertSegment_sameKeyDifferentUsers_separate() {
        adapter.upsertSegment(USER, "prefs", "user1-prefs");
        adapter.upsertSegment(USER_2, "prefs", "user2-prefs");

        assertThat(adapter.getSegments(USER).get(0).content()).isEqualTo("user1-prefs");
        assertThat(adapter.getSegments(USER_2).get(0).content()).isEqualTo("user2-prefs");
    }

    // ── deleteSegment ───────────────────────────────────────

    @Test
    @DisplayName("deleteSegment removes the segment")
    void deleteSegment_removesSegment() {
        adapter.upsertSegment(USER, "prefs", "data");
        adapter.upsertSegment(USER, "profile", "data2");

        adapter.deleteSegment(USER, "prefs");

        List<KnowledgeSegment> segments = adapter.getSegments(USER);
        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).segmentKey()).isEqualTo("profile");
    }

    @Test
    @DisplayName("deleteSegment on nonexistent key does not throw")
    void deleteSegment_nonexistentKey_noException() {
        adapter.upsertSegment(USER, "prefs", "data");
        adapter.deleteSegment(USER, "nonexistent"); // should not throw

        assertThat(adapter.getSegments(USER)).hasSize(1);
    }

    @Test
    @DisplayName("deleteSegment on nonexistent user does not throw")
    void deleteSegment_nonexistentUser_noException() {
        adapter.deleteSegment("no-user", "no-key"); // should not throw
    }

    @Test
    @DisplayName("deleteSegment does not affect other users with same key")
    void deleteSegment_doesNotAffectOtherUsers() {
        adapter.upsertSegment(USER, "prefs", "u1");
        adapter.upsertSegment(USER_2, "prefs", "u2");

        adapter.deleteSegment(USER, "prefs");

        assertThat(adapter.getSegments(USER)).isEmpty();
        assertThat(adapter.getSegments(USER_2)).hasSize(1);
    }

    // ── Thread safety ───────────────────────────────────────

    @Test
    @DisplayName("concurrent upserts and reads do not corrupt state")
    void concurrentUpserts_threadSafe() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int s = 0; s < 10; s++) {
                        adapter.upsertSegment(USER, "key-" + threadId + "-" + s, "val");
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Each thread wrote 10 unique keys
        assertThat(adapter.getSegments(USER)).hasSize(threadCount * 10);
    }
}
