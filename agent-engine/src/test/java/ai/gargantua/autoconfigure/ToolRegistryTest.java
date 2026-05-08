package ai.gargantua.autoconfigure;

import ai.gargantua.core.tool.AgentTool;
import ai.gargantua.core.tool.RequiresApproval;
import ai.gargantua.core.tool.ToolDefinition;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;

import java.util.Collection;
import java.util.List;

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
}
