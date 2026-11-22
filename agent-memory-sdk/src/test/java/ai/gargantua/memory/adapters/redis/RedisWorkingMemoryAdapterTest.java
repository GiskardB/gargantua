package ai.gargantua.memory.adapters.redis;

import ai.gargantua.core.memory.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisKeyCommands;
import org.springframework.data.redis.connection.RedisListCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisWorkingMemoryAdapterTest {

    private StringRedisTemplate redis;
    private RedisConnection connection;
    private RedisListCommands lists;
    private RedisKeyCommands keys;
    private RedisWorkingMemoryAdapter adapter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        connection = mock(RedisConnection.class);
        lists = mock(RedisListCommands.class);
        keys = mock(RedisKeyCommands.class);
        when(connection.listCommands()).thenReturn(lists);
        when(connection.keyCommands()).thenReturn(keys);
        // Run the pipelined callback against the mock connection, as the template would.
        when(redis.executePipelined(any(RedisCallback.class))).thenAnswer(invocation -> {
            invocation.<RedisCallback<Object>>getArgument(0).doInRedis(connection);
            return List.of();
        });
        adapter = new RedisWorkingMemoryAdapter(redis, 20, 30);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aWholeTurnIsWrittenInOnePipeline() {
        adapter.appendMessages("s1", List.of(
                ChatMessage.userMessage("hi"), ChatMessage.assistantMessage("hello")));

        verify(redis, times(1)).executePipelined(any(RedisCallback.class));

        byte[] key = "working_memory:s1".getBytes(StandardCharsets.UTF_8);
        ArgumentCaptor<byte[][]> pushed = ArgumentCaptor.forClass(byte[][].class);
        var order = inOrder(lists, keys);
        order.verify(lists).rPush(org.mockito.ArgumentMatchers.eq(key), pushed.capture());
        order.verify(lists).lTrim(key, -20, -1);
        order.verify(keys).expire(key, 30 * 60L);

        assertThat(pushed.getValue()).hasNumberOfRows(2);
        assertThat(new String(pushed.getValue()[0], StandardCharsets.UTF_8)).contains("\"role\":\"user\"");
        assertThat(new String(pushed.getValue()[1], StandardCharsets.UTF_8)).contains("\"role\":\"assistant\"");
    }

    @Test
    @SuppressWarnings("unchecked")
    void anEmptyBatchTouchesNothing() {
        adapter.appendMessages("s1", List.of());

        verify(redis, never()).executePipelined(any(RedisCallback.class));
    }

    @Test
    void serializationSurvivesQuotesNewlinesAndBackslashes() {
        var original = new ChatMessage("user", "say \"hi\"\nC:\\temp\tdone", Instant.parse("2026-01-02T03:04:05Z"));

        var restored = RedisWorkingMemoryAdapter.deserialize(RedisWorkingMemoryAdapter.serialize(original));

        assertThat(restored).isEqualTo(original);
    }
}
