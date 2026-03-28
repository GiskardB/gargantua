package ai.gargantua.memory.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the memory subsystem, bound to {@code agentkit.memory.*}.
 * Controls TTLs, message limits, and token budgets for each memory layer.
 */
@ConfigurationProperties(prefix = "agentkit.memory")
public class AgentMemoryProperties {

    private Working working = new Working();
    private Episodic episodic = new Episodic();
    private Knowledge knowledge = new Knowledge();
    private Composer composer = new Composer();

    public Working getWorking() {
        return working;
    }

    public void setWorking(Working working) {
        this.working = working;
    }

    public Episodic getEpisodic() {
        return episodic;
    }

    public void setEpisodic(Episodic episodic) {
        this.episodic = episodic;
    }

    public Knowledge getKnowledge() {
        return knowledge;
    }

    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
    }

    public Composer getComposer() {
        return composer;
    }

    public void setComposer(Composer composer) {
        this.composer = composer;
    }

    public static class Working {

        private int maxMessages = 20;
        private int ttlMinutes = 30;

        public int getMaxMessages() {
            return maxMessages;
        }

        public void setMaxMessages(int maxMessages) {
            this.maxMessages = maxMessages;
        }

        public int getTtlMinutes() {
            return ttlMinutes;
        }

        public void setTtlMinutes(int ttlMinutes) {
            this.ttlMinutes = ttlMinutes;
        }
    }

    public static class Episodic {

        private int maxSummaries = 5;
        private int ttlDays = 365;

        public int getMaxSummaries() {
            return maxSummaries;
        }

        public void setMaxSummaries(int maxSummaries) {
            this.maxSummaries = maxSummaries;
        }

        public int getTtlDays() {
            return ttlDays;
        }

        public void setTtlDays(int ttlDays) {
            this.ttlDays = ttlDays;
        }
    }

    public static class Knowledge {

        private int maxSegments = 10;
        private int maxTokensPerSegment = 400;

        public int getMaxSegments() {
            return maxSegments;
        }

        public void setMaxSegments(int maxSegments) {
            this.maxSegments = maxSegments;
        }

        public int getMaxTokensPerSegment() {
            return maxTokensPerSegment;
        }

        public void setMaxTokensPerSegment(int maxTokensPerSegment) {
            this.maxTokensPerSegment = maxTokensPerSegment;
        }
    }

    public static class Composer {

        private int maxContextTokens = 3000;

        public int getMaxContextTokens() {
            return maxContextTokens;
        }

        public void setMaxContextTokens(int maxContextTokens) {
            this.maxContextTokens = maxContextTokens;
        }
    }
}
