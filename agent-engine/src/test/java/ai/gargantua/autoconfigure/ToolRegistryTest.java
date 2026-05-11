package ai.gargantua.autoconfigure;

import ai.gargantua.core.tool.AgentTool;
import ai.gargantua.core.tool.CacheScope;
import ai.gargantua.core.tool.CacheableToolResult;
import ai.gargantua.core.tool.RequiresApproval;
import ai.gargantua.core.tool.ToolDefinition;
import ai.gargantua.core.tool.ToolRetry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ToolRegistry")
class ToolRegistryTest {

    @Mock
    private ApplicationContext applicationContext;

    private ToolRegistry toolRegistry;

    // Test bean with annotated methods
    static class SampleToolBean {
        @AgentTool(description = "Fetches weather data")
        public String getWeather(String city) {
            return "sunny";
        }

        @AgentTool(name = "custom-calculator", description = "Performs calculations", parallelizable = false)
        public double calculate(double a, double b) {
            return a + b;
        }

        @AgentTool(description = "Transfers money")
        @RequiresApproval(message = "Confirm transfer", dangerous = true)
        public String transferMoney(String from, String to) {
            return "done";
        }
    }

    static class BeanWithoutTools {
        public String normalMethod() {
            return "not a tool";
        }
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ObjectProvider<ToolResultCache> cacheProvider = mock(ObjectProvider.class);
        ObjectProvider<MeterRegistry> meterProvider = mock(ObjectProvider.class);
        toolRegistry = new ToolRegistry(applicationContext, cacheProvider, meterProvider);
    }

    @Nested
    @DisplayName("scan and discovery")
    class ScanAndDiscovery {

        @Test
        @DisplayName("discovers AgentTool-annotated methods from beans")
        void discoversAnnotatedMethods() {
            when(applicationContext.getBeanDefinitionNames())
                    .thenReturn(new String[]{"sampleToolBean"});
            when(applicationContext.getBean("sampleToolBean"))
                    .thenReturn(new SampleToolBean());

            toolRegistry.scan();

            Collection<ToolDefinition> tools = toolRegistry.getToolDefinitions();
            assertThat(tools).hasSize(3);
        }

        @Test
        @DisplayName("uses method name as tool name when annotation name is blank")
        void usesMethodNameWhenAnnotationNameBlank() {
            when(applicationContext.getBeanDefinitionNames())
                    .thenReturn(new String[]{"sampleToolBean"});
            when(applicationContext.getBean("sampleToolBean"))
                    .thenReturn(new SampleToolBean());

            toolRegistry.scan();

            List<String> names = toolRegistry.getAllToolNames();
            assertThat(names).contains("getWeather");
        }

        @Test
        @DisplayName("uses annotation name when explicitly set")
        void usesAnnotationNameWhenSet() {
            when(applicationContext.getBeanDefinitionNames())
                    .thenReturn(new String[]{"sampleToolBean"});
            when(applicationContext.getBean("sampleToolBean"))
                    .thenReturn(new SampleToolBean());

            toolRegistry.scan();

            List<String> names = toolRegistry.getAllToolNames();
            assertThat(names).contains("custom-calculator");
        }

        @Test
        @DisplayName("captures RequiresApproval metadata")
        void capturesRequiresApprovalMetadata() {
            when(applicationContext.getBeanDefinitionNames())
                    .thenReturn(new String[]{"sampleToolBean"});
            when(applicationContext.getBean("sampleToolBean"))
                    .thenReturn(new SampleToolBean());

            toolRegistry.scan();

            ToolDefinition transferTool = toolRegistry.getToolDefinitions().stream()
                    .filter(t -> t.name().equals("transferMoney"))
                    .findFirst()
                    .orElseThrow();

            assertThat(transferTool.requiresApproval()).isTrue();
            assertThat(transferTool.approvalMessage()).isEqualTo("Confirm transfer");
            assertThat(transferTool.dangerous()).isTrue();
        }

        @Test
        @DisplayName("captures parallelizable flag")
        void capturesParallelizableFlag() {
            when(applicationContext.getBeanDefinitionNames())
                    .thenReturn(new String[]{"sampleToolBean"});
            when(applicationContext.getBean("sampleToolBean"))
                    .thenReturn(new SampleToolBean());

            toolRegistry.scan();

            ToolDefinition calcTool = toolRegistry.getToolDefinitions().stream()
                    .filter(t -> t.name().equals("custom-calculator"))
                    .findFirst()
                    .orElseThrow();

            assertThat(calcTool.parallelizable()).isFalse();
        }

        @Test
        @DisplayName("skips beans without AgentTool annotations")
        void skipsBeanWithoutAnnotations() {
            when(applicationContext.getBeanDefinitionNames())
                    .thenReturn(new String[]{"plainBean"});
            when(applicationContext.getBean("plainBean"))
                    .thenReturn(new BeanWithoutTools());

            toolRegistry.scan();

            assertThat(toolRegistry.getToolDefinitions()).isEmpty();
        }

        @Test
        @DisplayName("handles bean retrieval exceptions gracefully")
        void handlesBeanRetrievalExceptions() {
            when(applicationContext.getBeanDefinitionNames())
                    .thenReturn(new String[]{"badBean", "sampleToolBean"});
            when(applicationContext.getBean("badBean"))
                    .thenThrow(new RuntimeException("cannot create bean"));
            when(applicationContext.getBean("sampleToolBean"))
                    .thenReturn(new SampleToolBean());

            toolRegistry.scan();

            // Should have discovered tools from sampleToolBean despite badBean failure
            assertThat(toolRegistry.getToolDefinitions()).isNotEmpty();
        }

        @Test
        @DisplayName("returns empty when no beans have tools")
        void returnsEmptyWhenNoToolBeans() {
            when(applicationContext.getBeanDefinitionNames())
                    .thenReturn(new String[]{});

            toolRegistry.scan();

            assertThat(toolRegistry.getToolDefinitions()).isEmpty();
            assertThat(toolRegistry.getAllToolNames()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getFilteredTools")
    class GetFilteredTools {

        @BeforeEach
        void scanTools() {
            when(applicationContext.getBeanDefinitionNames())
                    .thenReturn(new String[]{"sampleToolBean"});
            when(applicationContext.getBean("sampleToolBean"))
                    .thenReturn(new SampleToolBean());
            toolRegistry.scan();
        }

        @Test
        @DisplayName("returns all tools when allowedTools is null")
        void returnsAllWhenNull() {
            Collection<ToolDefinition> tools = toolRegistry.getFilteredTools(null);
            assertThat(tools).hasSize(3);
        }

        @Test
        @DisplayName("returns all tools when allowedTools is empty")
        void returnsAllWhenEmpty() {
            Collection<ToolDefinition> tools = toolRegistry.getFilteredTools(List.of());
            assertThat(tools).hasSize(3);
        }

        @Test
        @DisplayName("filters to only allowed tools")
        void filtersToAllowedTools() {
            Collection<ToolDefinition> tools = toolRegistry.getFilteredTools(
                    List.of("getWeather", "transferMoney"));
            assertThat(tools).hasSize(2);
            assertThat(tools).extracting(ToolDefinition::name)
                    .containsExactlyInAnyOrder("getWeather", "transferMoney");
        }

        @Test
        @DisplayName("returns empty when no allowed tools match")
        void returnsEmptyWhenNoMatch() {
            Collection<ToolDefinition> tools = toolRegistry.getFilteredTools(
                    List.of("nonexistent-tool"));
            assertThat(tools).isEmpty();
        }

        @Test
        @DisplayName("filters to single tool")
        void filtersToSingleTool() {
            Collection<ToolDefinition> tools = toolRegistry.getFilteredTools(
                    List.of("custom-calculator"));
            assertThat(tools).hasSize(1);
            assertThat(tools.iterator().next().name()).isEqualTo("custom-calculator");
        }
    }

    @Nested
    @DisplayName("executeTool")
    class ExecuteTool {

        static class ArithmeticToolBean {
            @AgentTool(description = "Adds two integers and returns their sum")
            public int add(int a, int b) {
                return a + b;
            }

            public record MultiplyResult(int a, int b, long result) {}

            @AgentTool(description = "Multiplies two integers, returns a record")
            public MultiplyResult multiply(int a, int b) {
                return new MultiplyResult(a, b, (long) a * b);
            }

            @AgentTool(description = "Always throws — exercises the exception path")
            public String boom(String why) {
                throw new IllegalArgumentException("nope: " + why);
            }
        }

        @BeforeEach
        void scanArithmetic() {
            when(applicationContext.getBeanDefinitionNames())
                    .thenReturn(new String[]{"arithmeticToolBean"});
            when(applicationContext.getBean("arithmeticToolBean"))
                    .thenReturn(new ArithmeticToolBean());
            toolRegistry.scan();
        }

        @Test
        @DisplayName("primitive return is JSON-encoded as a bare value")
        void primitiveReturnIsJsonEncoded() {
            String result = toolRegistry.executeTool("add", "{\"a\":\"2\",\"b\":\"3\"}");
            assertThat(result).isEqualTo("5");
        }

        @Test
        @DisplayName("record return is JSON-encoded as an object with field names")
        void recordReturnIsJsonEncoded() {
            String result = toolRegistry.executeTool("multiply", "{\"a\":\"3\",\"b\":\"4\"}");
            assertThat(result).contains("\"a\":3", "\"b\":4", "\"result\":12");
        }

        @Test
        @DisplayName("unknown tool name returns {\"error\":\"Tool not found: …\"}")
        void unknownToolReturnsErrorJson() {
            String result = toolRegistry.executeTool("nope", "{}");
            assertThat(result).startsWith("{\"error\":").contains("Tool not found", "nope");
        }

        @Test
        @DisplayName("exception thrown by the tool body is wrapped in errorJson (no @ToolRetry)")
        void exceptionFromToolBodyIsWrappedInErrorJson() {
            // No @ToolRetry on `boom`. Before the 1.2.3 fix this used to
            // propagate the IllegalArgumentException out of executeTool —
            // now the contract is that any RuntimeException becomes a
            // structured {"error":"..."} payload the LLM can read.
            String result = toolRegistry.executeTool("boom", "{\"why\":\"on purpose\"}");
            assertThat(result)
                    .startsWith("{\"error\":")
                    .contains("Tool execution failed", "on purpose");
        }
    }

    /**
     * Regression coverage for the 1.2.4 fix: when a tool method throws a
     * checked exception, the framework wraps it through a sentinel
     * {@code CheckedToolException} so the retry predicate still sees the
     * original type instead of a bare {@code RuntimeException}.
     */
    @Nested
    @DisplayName("executeTool — @ToolRetry on checked exceptions (1.2.4 regression)")
    class RetryOnCheckedExceptions {

        static class FlakyToolBean {
            final AtomicInteger invocations = new AtomicInteger();
            volatile int failuresBeforeSuccess = 2;

            @AgentTool(description = "Throws IOException until the Nth attempt, then succeeds")
            @ToolRetry(
                    maxAttempts = 3,
                    waitDurationMs = 1,
                    backoffMultiplier = 1.0,
                    retryOn = {IOException.class}
            )
            public String flaky(String input) throws IOException {
                int n = invocations.incrementAndGet();
                if (n <= failuresBeforeSuccess) {
                    throw new IOException("transient failure on attempt " + n);
                }
                return "ok:" + input;
            }

            @AgentTool(description = "Always throws IOException — exhausts the retry budget")
            @ToolRetry(maxAttempts = 3, waitDurationMs = 1, retryOn = {IOException.class})
            public String alwaysFail(String reason) throws IOException {
                invocations.incrementAndGet();
                throw new IOException("permanent: " + reason);
            }
        }

        private FlakyToolBean bean;
        private SimpleMeterRegistry meters;

        @BeforeEach
        @SuppressWarnings("unchecked")
        void wireRealMeterRegistry() {
            // Replace the parent-class setUp's mocked meter provider with a
            // real SimpleMeterRegistry so we can assert the retry counters.
            meters = new SimpleMeterRegistry();
            ObjectProvider<ToolResultCache> cacheProvider = mock(ObjectProvider.class);
            ObjectProvider<MeterRegistry>   meterProvider = mock(ObjectProvider.class);
            when(meterProvider.getIfAvailable()).thenReturn(meters);

            toolRegistry = new ToolRegistry(applicationContext, cacheProvider, meterProvider);

            bean = new FlakyToolBean();
            when(applicationContext.getBeanDefinitionNames())
                    .thenReturn(new String[]{"flakyToolBean"});
            when(applicationContext.getBean("flakyToolBean")).thenReturn(bean);
            toolRegistry.scan();
        }

        @Test
        @DisplayName("retries an IOException-throwing tool until it succeeds")
        void retriesUntilSuccess() {
            String result = toolRegistry.executeTool("flaky", "{\"input\":\"hi\"}");

            assertThat(result).isEqualTo("ok:hi");
            assertThat(bean.invocations.get()).isEqualTo(3);
            // 2 retries means the onRetry event fires twice.
            assertThat(meters.counter("agent.tool.retry.attempts", "tool", "flaky").count())
                    .isEqualTo(2.0);
        }

        @Test
        @DisplayName("exhausts maxAttempts when IOException is permanent")
        void exhaustsRetryBudget() {
            String result = toolRegistry.executeTool("alwaysFail", "{\"reason\":\"x\"}");

            assertThat(result).startsWith("{\"error\":").contains("Tool execution failed");
            assertThat(bean.invocations.get()).isEqualTo(3);
            assertThat(meters.counter("agent.tool.retry.exhausted", "tool", "alwaysFail").count())
                    .isEqualTo(1.0);
        }
    }

    /**
     * End-to-end coverage for {@link CacheableToolResult} backed by the
     * in-memory {@link ToolResultCache} (1.2.5). Verifies the read-through
     * behaviour, hit/miss counters, and that distinct argument tuples land
     * in distinct cache slots.
     */
    @Nested
    @DisplayName("executeTool — @CacheableToolResult (1.2.5)")
    class CacheableToolResults {

        static class CounterToolBean {
            final AtomicInteger invocations = new AtomicInteger();

            @AgentTool(description = "Returns the call count for the given key — purely to make the cache observable")
            @CacheableToolResult(ttlSeconds = 60, keyParams = {"key"}, scope = CacheScope.GLOBAL)
            public String count(String key) {
                return "k=" + key + ";n=" + invocations.incrementAndGet();
            }
        }

        private CounterToolBean bean;
        private SimpleMeterRegistry meters;
        private ToolResultCache cache;

        @BeforeEach
        @SuppressWarnings("unchecked")
        void wireRealCacheAndMeters() {
            meters = new SimpleMeterRegistry();
            cache  = new ToolResultCache();

            ObjectProvider<ToolResultCache> cacheProvider = mock(ObjectProvider.class);
            ObjectProvider<MeterRegistry>   meterProvider = mock(ObjectProvider.class);
            when(cacheProvider.getIfAvailable()).thenReturn(cache);
            when(meterProvider.getIfAvailable()).thenReturn(meters);

            toolRegistry = new ToolRegistry(applicationContext, cacheProvider, meterProvider);

            bean = new CounterToolBean();
            when(applicationContext.getBeanDefinitionNames())
                    .thenReturn(new String[]{"counterToolBean"});
            when(applicationContext.getBean("counterToolBean")).thenReturn(bean);
            toolRegistry.scan();
        }

        @Test
        @DisplayName("second call with the same args returns the cached value — tool body runs once")
        void secondCallHitsCache() {
            String first  = toolRegistry.executeTool("count", "{\"key\":\"alpha\"}");
            String second = toolRegistry.executeTool("count", "{\"key\":\"alpha\"}");

            // doInvoke returns String values verbatim (no JSON quoting).
            assertThat(first).isEqualTo("k=alpha;n=1");
            assertThat(second).isEqualTo(first);
            assertThat(bean.invocations.get())
                    .as("tool body should have executed exactly once across two invocations")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("distinct keyParams values land in distinct cache slots")
        void distinctArgsAreDistinctCacheEntries() {
            toolRegistry.executeTool("count", "{\"key\":\"alpha\"}");
            toolRegistry.executeTool("count", "{\"key\":\"beta\"}");
            toolRegistry.executeTool("count", "{\"key\":\"alpha\"}"); // should hit
            toolRegistry.executeTool("count", "{\"key\":\"beta\"}");  // should hit

            assertThat(bean.invocations.get())
                    .as("alpha and beta each execute once, the repeats hit the cache")
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("Micrometer cache hit/miss counters increment per scope")
        void hitMissMetersIncrement() {
            toolRegistry.executeTool("count", "{\"key\":\"alpha\"}"); // miss
            toolRegistry.executeTool("count", "{\"key\":\"alpha\"}"); // hit
            toolRegistry.executeTool("count", "{\"key\":\"alpha\"}"); // hit

            assertThat(meters.counter("agent.tool.cache.misses",
                    "tool", "count", "scope", "GLOBAL").count())
                    .isEqualTo(1.0);
            assertThat(meters.counter("agent.tool.cache.hits",
                    "tool", "count", "scope", "GLOBAL").count())
                    .isEqualTo(2.0);
        }
    }
}
