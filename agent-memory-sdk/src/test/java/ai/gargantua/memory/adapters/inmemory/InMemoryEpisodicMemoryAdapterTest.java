package ai.gargantua.memory.adapters.inmemory;

import ai.gargantua.core.memory.SessionSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryEpisodicMemoryAdapterTest {

    private static final String USER = "user-1";
    private static final String USER_2 = "user-2";

    private InMemoryEpisodicMemoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new InMemoryEpisodicMemoryAdapter();
    }

    // ── getRecentSummaries ──────────────────────────────────

    @Test
    @DisplayName("getRecentSummaries returns empty list for unknown user")
    void getRecentSummaries_unknownUser_returnsEmpty() {
        assertThat(adapter.getRecentSummaries("nonexistent", 10)).isEmpty();
    }

    @Test
    @DisplayName("getRecentSummaries returns empty list when no summaries exist")
    void getRecentSummaries_noSummaries_returnsEmpty() {
        assertThat(adapter.getRecentSummaries(USER, 5)).isEmpty();
    }

    @Test
    @DisplayName("getRecentSummaries returns summaries sorted by sessionDate descending")
    void getRecentSummaries_sortedByDateDesc() {
        Instant now = Instant.now();
        saveSummary(USER, "oldest", now.minus(3, ChronoUnit.DAYS));
        saveSummary(USER, "middle", now.minus(1, ChronoUnit.DAYS));
        saveSummary(USER, "newest", now);

        List<SessionSummary> result = adapter.getRecentSummaries(USER, 10);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).sessionId()).isEqualTo("newest");
        assertThat(result.get(1).sessionId()).isEqualTo("middle");
        assertThat(result.get(2).sessionId()).isEqualTo("oldest");
    }

    @Test
    @DisplayName("getRecentSummaries respects limit parameter")
    void getRecentSummaries_respectsLimit() {
        Instant now = Instant.now();
        for (int i = 0; i < 10; i++) {
            saveSummary(USER, "session-" + i, now.minus(i, ChronoUnit.HOURS));
        }

        List<SessionSummary> result = adapter.getRecentSummaries(USER, 3);

        assertThat(result).hasSize(3);
        // Should return the 3 most recent
        assertThat(result.get(0).sessionId()).isEqualTo("session-0");
        assertThat(result.get(1).sessionId()).isEqualTo("session-1");
        assertThat(result.get(2).sessionId()).isEqualTo("session-2");
    }

    @Test
    @DisplayName("getRecentSummaries returns all when limit exceeds count")
    void getRecentSummaries_limitExceedsCount_returnsAll() {
        saveSummary(USER, "only-one", Instant.now());

        List<SessionSummary> result = adapter.getRecentSummaries(USER, 100);
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("getRecentSummaries with limit=0 returns empty list")
    void getRecentSummaries_limitZero_returnsEmpty() {
        saveSummary(USER, "s1", Instant.now());

        assertThat(adapter.getRecentSummaries(USER, 0)).isEmpty();
    }

    // ── saveSummary ─────────────────────────────────────────

    @Test
    @DisplayName("saveSummary persists summary and makes it retrievable")
    void saveSummary_persistsSummary() {
        SessionSummary summary = new SessionSummary(
                USER, "s1", "Talked about Java",
                List.of("Java"), List.of("deploy issue"), 5, Instant.now()
        );

        adapter.saveSummary(summary);

        List<SessionSummary> result = adapter.getRecentSummaries(USER, 10);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).summary()).isEqualTo("Talked about Java");
        assertThat(result.get(0).keyTopics()).containsExactly("Java");
        assertThat(result.get(0).unresolvedItems()).containsExactly("deploy issue");
        assertThat(result.get(0).messageCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("saveSummary isolates users from each other")
    void saveSummary_userIsolation() {
        saveSummary(USER, "s1", Instant.now());
        saveSummary(USER_2, "s2", Instant.now());

        assertThat(adapter.getRecentSummaries(USER, 10)).hasSize(1);
        assertThat(adapter.getRecentSummaries(USER_2, 10)).hasSize(1);
        assertThat(adapter.getRecentSummaries(USER, 10).get(0).sessionId()).isEqualTo("s1");
        assertThat(adapter.getRecentSummaries(USER_2, 10).get(0).sessionId()).isEqualTo("s2");
    }

    @Test
    @DisplayName("saveSummary allows multiple summaries for the same user")
    void saveSummary_multipleSummariesPerUser() {
        Instant now = Instant.now();
        saveSummary(USER, "s1", now.minus(2, ChronoUnit.HOURS));
        saveSummary(USER, "s2", now.minus(1, ChronoUnit.HOURS));
        saveSummary(USER, "s3", now);

        assertThat(adapter.getRecentSummaries(USER, 10)).hasSize(3);
    }

    // ── Thread safety ───────────────────────────────────────

    @Test
    @DisplayName("concurrent saveSummary calls do not lose data")
    void concurrentSaveSummary_threadSafe() throws InterruptedException {
        int threadCount = 10;
        int summariesPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        Instant base = Instant.now();
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int s = 0; s < summariesPerThread; s++) {
                        saveSummary(USER, "t" + threadId + "-s" + s,
                                base.minus(threadId * summariesPerThread + s, ChronoUnit.MINUTES));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(adapter.getRecentSummaries(USER, 1000))
                .hasSize(threadCount * summariesPerThread);
    }

    // ── Helpers ──────────────────────────────────────────────

    private void saveSummary(String userId, String sessionId, Instant sessionDate) {
        adapter.saveSummary(new SessionSummary(
                userId, sessionId, "Summary for " + sessionId,
                List.of("topic"), List.of(), 5, sessionDate
        ));
    }
}
