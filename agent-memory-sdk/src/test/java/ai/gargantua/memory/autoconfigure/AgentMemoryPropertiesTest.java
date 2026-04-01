package ai.gargantua.memory.autoconfigure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentMemoryPropertiesTest {

    // ── Default values ──────────────────────────────────────

    @Nested
    @DisplayName("Default values")
    class Defaults {

        private final AgentMemoryProperties props = new AgentMemoryProperties();

        @Test
        @DisplayName("working.maxMessages defaults to 20")
        void workingMaxMessages_defaultIs20() {
            assertThat(props.getWorking().getMaxMessages()).isEqualTo(20);
        }

        @Test
        @DisplayName("working.ttlMinutes defaults to 30")
        void workingTtlMinutes_defaultIs30() {
            assertThat(props.getWorking().getTtlMinutes()).isEqualTo(30);
        }

        @Test
        @DisplayName("episodic.maxSummaries defaults to 5")
        void episodicMaxSummaries_defaultIs5() {
            assertThat(props.getEpisodic().getMaxSummaries()).isEqualTo(5);
        }

        @Test
        @DisplayName("episodic.ttlDays defaults to 365")
        void episodicTtlDays_defaultIs365() {
            assertThat(props.getEpisodic().getTtlDays()).isEqualTo(365);
        }

        @Test
        @DisplayName("knowledge.maxSegments defaults to 10")
        void knowledgeMaxSegments_defaultIs10() {
            assertThat(props.getKnowledge().getMaxSegments()).isEqualTo(10);
        }

        @Test
        @DisplayName("knowledge.maxTokensPerSegment defaults to 400")
        void knowledgeMaxTokensPerSegment_defaultIs400() {
            assertThat(props.getKnowledge().getMaxTokensPerSegment()).isEqualTo(400);
        }

        @Test
        @DisplayName("composer.maxContextTokens defaults to 3000")
        void composerMaxContextTokens_defaultIs3000() {
            assertThat(props.getComposer().getMaxContextTokens()).isEqualTo(3000);
        }
    }

    // ── Nested objects are initialized ──────────────────────

    @Test
    @DisplayName("all nested property objects are non-null by default")
    void nestedObjects_areNonNull() {
        AgentMemoryProperties props = new AgentMemoryProperties();

        assertThat(props.getWorking()).isNotNull();
        assertThat(props.getEpisodic()).isNotNull();
        assertThat(props.getKnowledge()).isNotNull();
        assertThat(props.getComposer()).isNotNull();
    }

    // ── Setters ─────────────────────────────────────────────

    @Nested
    @DisplayName("Setters override defaults")
    class Setters {

        @Test
        @DisplayName("setWorking replaces working config")
        void setWorking_replacesConfig() {
            AgentMemoryProperties props = new AgentMemoryProperties();
            AgentMemoryProperties.Working custom = new AgentMemoryProperties.Working();
            custom.setMaxMessages(50);
            custom.setTtlMinutes(60);

            props.setWorking(custom);

            assertThat(props.getWorking().getMaxMessages()).isEqualTo(50);
            assertThat(props.getWorking().getTtlMinutes()).isEqualTo(60);
        }

        @Test
        @DisplayName("setEpisodic replaces episodic config")
        void setEpisodic_replacesConfig() {
            AgentMemoryProperties props = new AgentMemoryProperties();
            AgentMemoryProperties.Episodic custom = new AgentMemoryProperties.Episodic();
            custom.setMaxSummaries(10);
            custom.setTtlDays(730);

            props.setEpisodic(custom);

            assertThat(props.getEpisodic().getMaxSummaries()).isEqualTo(10);
            assertThat(props.getEpisodic().getTtlDays()).isEqualTo(730);
        }

        @Test
        @DisplayName("setKnowledge replaces knowledge config")
        void setKnowledge_replacesConfig() {
            AgentMemoryProperties props = new AgentMemoryProperties();
            AgentMemoryProperties.Knowledge custom = new AgentMemoryProperties.Knowledge();
            custom.setMaxSegments(25);
            custom.setMaxTokensPerSegment(800);

            props.setKnowledge(custom);

            assertThat(props.getKnowledge().getMaxSegments()).isEqualTo(25);
            assertThat(props.getKnowledge().getMaxTokensPerSegment()).isEqualTo(800);
        }

        @Test
        @DisplayName("setComposer replaces composer config")
        void setComposer_replacesConfig() {
            AgentMemoryProperties props = new AgentMemoryProperties();
            AgentMemoryProperties.Composer custom = new AgentMemoryProperties.Composer();
            custom.setMaxContextTokens(8000);

            props.setComposer(custom);

            assertThat(props.getComposer().getMaxContextTokens()).isEqualTo(8000);
        }

        @Test
        @DisplayName("individual field setters on Working update values")
        void workingFieldSetters() {
            AgentMemoryProperties.Working working = new AgentMemoryProperties.Working();
            working.setMaxMessages(100);
            working.setTtlMinutes(120);

            assertThat(working.getMaxMessages()).isEqualTo(100);
            assertThat(working.getTtlMinutes()).isEqualTo(120);
        }

        @Test
        @DisplayName("individual field setters on Episodic update values")
        void episodicFieldSetters() {
            AgentMemoryProperties.Episodic episodic = new AgentMemoryProperties.Episodic();
            episodic.setMaxSummaries(20);
            episodic.setTtlDays(180);

            assertThat(episodic.getMaxSummaries()).isEqualTo(20);
            assertThat(episodic.getTtlDays()).isEqualTo(180);
        }

        @Test
        @DisplayName("individual field setters on Knowledge update values")
        void knowledgeFieldSetters() {
            AgentMemoryProperties.Knowledge knowledge = new AgentMemoryProperties.Knowledge();
            knowledge.setMaxSegments(50);
            knowledge.setMaxTokensPerSegment(1000);

            assertThat(knowledge.getMaxSegments()).isEqualTo(50);
            assertThat(knowledge.getMaxTokensPerSegment()).isEqualTo(1000);
        }

        @Test
        @DisplayName("individual field setters on Composer update values")
        void composerFieldSetters() {
            AgentMemoryProperties.Composer composer = new AgentMemoryProperties.Composer();
            composer.setMaxContextTokens(16000);

            assertThat(composer.getMaxContextTokens()).isEqualTo(16000);
        }
    }
}
