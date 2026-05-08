package ai.gargantua.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Master configuration properties for the Gargantua Agent Framework,
 * bound to the {@code agent.*} prefix. Controls all aspects of the agent:
 * API metadata, skill discovery, LLM providers, routing, memory, guardrails,
 * HITL, cost tracking, dry-run, chat history, observability, and MCP.
 */
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    private Api api = new Api();
    private Skill skill = new Skill();
    private Llm llm = new Llm();
    private Routing routing = new Routing();
    private Memory memory = new Memory();
    private Guardrail guardrail = new Guardrail();
    private Output output = new Output();
    private Hitl hitl = new Hitl();
    private CostTracking costTracking = new CostTracking();
    private DryRun dryRun = new DryRun();
    private ChatHistory chatHistory = new ChatHistory();
    private Observability observability = new Observability();
    private Enrichers enrichers = new Enrichers();
    private Mcp mcp = new Mcp();
    private Skillsjars skillsjars = new Skillsjars();
    private Audit audit = new Audit();
    private ChatUi chatUi = new ChatUi();

    // --- getters and setters ---

    public Api getApi() { return api; }
    public void setApi(Api api) { this.api = api; }

    public Skill getSkill() { return skill; }
    public void setSkill(Skill skill) { this.skill = skill; }

    public Llm getLlm() { return llm; }
    public void setLlm(Llm llm) { this.llm = llm; }

    public Routing getRouting() { return routing; }
    public void setRouting(Routing routing) { this.routing = routing; }

    public Memory getMemory() { return memory; }
    public void setMemory(Memory memory) { this.memory = memory; }

    public Guardrail getGuardrail() { return guardrail; }
    public void setGuardrail(Guardrail guardrail) { this.guardrail = guardrail; }

    public Output getOutput() { return output; }
    public void setOutput(Output output) { this.output = output; }

    public Hitl getHitl() { return hitl; }
    public void setHitl(Hitl hitl) { this.hitl = hitl; }

    public CostTracking getCostTracking() { return costTracking; }
    public void setCostTracking(CostTracking costTracking) { this.costTracking = costTracking; }


    public DryRun getDryRun() { return dryRun; }
    public void setDryRun(DryRun dryRun) { this.dryRun = dryRun; }

    public ChatHistory getChatHistory() { return chatHistory; }
    public void setChatHistory(ChatHistory chatHistory) { this.chatHistory = chatHistory; }

    public Observability getObservability() { return observability; }
    public void setObservability(Observability observability) { this.observability = observability; }


    public Enrichers getEnrichers() { return enrichers; }
    public void setEnrichers(Enrichers enrichers) { this.enrichers = enrichers; }

    public Mcp getMcp() { return mcp; }
    public void setMcp(Mcp mcp) { this.mcp = mcp; }

    public Skillsjars getSkillsjars() { return skillsjars; }
    public void setSkillsjars(Skillsjars skillsjars) { this.skillsjars = skillsjars; }

    public Audit getAudit() { return audit; }
    public void setAudit(Audit audit) { this.audit = audit; }

    public ChatUi getChatUi() { return chatUi; }
    public void setChatUi(ChatUi chatUi) { this.chatUi = chatUi; }

    private Summarization summarization = new Summarization();
    public Summarization getSummarization() { return summarization; }
    public void setSummarization(Summarization summarization) { this.summarization = summarization; }

    // ==================== Nested classes ====================

    public static class Api {
        private String title = "AgentKit";
        private String version = "1.0.0";
        private String description = "";
        private String contactEmail = "";
        private String agentId = "default-agent";
        private String displayName = "AgentKit Agent";

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getContactEmail() { return contactEmail; }
        public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

        public String getAgentId() { return agentId; }
        public void setAgentId(String agentId) { this.agentId = agentId; }

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
    }

    public static class Skill {
        private String path = "skills";
        private boolean hotReload = false;
        private int cacheTtlMinutes = 10;
        private Cache cache = new Cache();

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }

        public boolean isHotReload() { return hotReload; }
        public void setHotReload(boolean hotReload) { this.hotReload = hotReload; }

        public int getCacheTtlMinutes() { return cacheTtlMinutes; }
        public void setCacheTtlMinutes(int cacheTtlMinutes) { this.cacheTtlMinutes = cacheTtlMinutes; }

        public Cache getCache() { return cache; }
        public void setCache(Cache cache) { this.cache = cache; }

        /**
         * Fine-grained controls for the skill registry cache.
         * When {@code ttlSeconds} is {@code 0}, the legacy {@code cacheTtlMinutes}
         * is used instead, preserving older configurations.
         */
        public static class Cache {
            private int ttlSeconds = 0;
            private int maxSize = 200;

            public int getTtlSeconds() { return ttlSeconds; }
            public void setTtlSeconds(int ttlSeconds) { this.ttlSeconds = ttlSeconds; }

            public int getMaxSize() { return maxSize; }
            public void setMaxSize(int maxSize) { this.maxSize = maxSize; }
        }
    }

    public static class Llm {
        private Primary primary = new Primary();
        private Fallback fallback = new Fallback();
        private RoutingModel routingModel = new RoutingModel();
        private Map<String, LlmModelConfig> models = new HashMap<>();
        private String primaryAlias = "default";
        private String fallbackAlias = "";
        private List<RoutingRule> routingRules = new ArrayList<>();
        private RateLimit rateLimit = new RateLimit();

        public RateLimit getRateLimit() { return rateLimit; }
        public void setRateLimit(RateLimit rateLimit) { this.rateLimit = rateLimit; }

        /**
         * Per-alias Resilience4j rate limit applied around every LLM call.
         * Default: 60 requests / 60 seconds, matching the historical hardcoded value.
         */
        public static class RateLimit {
            private int maxRequests = 60;
            private int windowSeconds = 60;

            public int getMaxRequests() { return maxRequests; }
            public void setMaxRequests(int maxRequests) { this.maxRequests = maxRequests; }

            public int getWindowSeconds() { return windowSeconds; }
            public void setWindowSeconds(int windowSeconds) { this.windowSeconds = windowSeconds; }
        }

        public Primary getPrimary() { return primary; }
        public void setPrimary(Primary primary) { this.primary = primary; }

        public Fallback getFallback() { return fallback; }
        public void setFallback(Fallback fallback) { this.fallback = fallback; }

        public RoutingModel getRoutingModel() { return routingModel; }
        public void setRoutingModel(RoutingModel routingModel) { this.routingModel = routingModel; }

        public Map<String, LlmModelConfig> getModels() { return models; }
        public void setModels(Map<String, LlmModelConfig> models) { this.models = models; }

        public String getPrimaryAlias() { return primaryAlias; }
        public void setPrimaryAlias(String primaryAlias) { this.primaryAlias = primaryAlias; }

        public String getFallbackAlias() { return fallbackAlias; }
        public void setFallbackAlias(String fallbackAlias) { this.fallbackAlias = fallbackAlias; }

        public List<RoutingRule> getRoutingRules() { return routingRules; }
        public void setRoutingRules(List<RoutingRule> routingRules) { this.routingRules = routingRules; }

        public static class Primary extends LlmModelConfig {
        }

        public static class Fallback extends LlmModelConfig {
        }

        public static class RoutingModel extends LlmModelConfig {
        }
    }

    public static class LlmModelConfig {
        private String provider = "openai";
        private String model = "gpt-4o";
        private String apiKey = "";
        private String endpoint = "";
        private double temperature = 0.7;
        private int maxTokens = 4096;

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }

        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    }

    public static class RoutingRule {
        private String name = "";
        private int priority = 100;
        private String description = "";
        private boolean enabled = true;
        private Map<String, Object> condition = new HashMap<>();
        private String targetModel = "";

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public Map<String, Object> getCondition() { return condition; }
        public void setCondition(Map<String, Object> condition) { this.condition = condition; }

        public String getTargetModel() { return targetModel; }
        public void setTargetModel(String targetModel) { this.targetModel = targetModel; }
    }

    public static class Routing {
        private String strategy = "hybrid";
        private String fallbackSkill = "default";
        private Semantic semantic = new Semantic();

        public String getStrategy() { return strategy; }
        public void setStrategy(String strategy) { this.strategy = strategy; }

        public String getFallbackSkill() { return fallbackSkill; }
        public void setFallbackSkill(String fallbackSkill) { this.fallbackSkill = fallbackSkill; }

        public Semantic getSemantic() { return semantic; }
        public void setSemantic(Semantic semantic) { this.semantic = semantic; }

        public static class Semantic {
            private double threshold = 0.6;
            private String model = "all-MiniLM-L6-v2";

            public double getThreshold() { return threshold; }
            public void setThreshold(double threshold) { this.threshold = threshold; }

            public String getModel() { return model; }
            public void setModel(String model) { this.model = model; }
        }
    }

    public static class Memory {
        private Working working = new Working();
        private Episodic episodic = new Episodic();
        private Knowledge knowledge = new Knowledge();
        private Composer composer = new Composer();

        public Working getWorking() { return working; }
        public void setWorking(Working working) { this.working = working; }

        public Episodic getEpisodic() { return episodic; }
        public void setEpisodic(Episodic episodic) { this.episodic = episodic; }

        public Knowledge getKnowledge() { return knowledge; }
        public void setKnowledge(Knowledge knowledge) { this.knowledge = knowledge; }

        public Composer getComposer() { return composer; }
        public void setComposer(Composer composer) { this.composer = composer; }

        public static class Working {
            private int maxMessages = 20;
            private int ttlMinutes = 30;

            public int getMaxMessages() { return maxMessages; }
            public void setMaxMessages(int maxMessages) { this.maxMessages = maxMessages; }

            public int getTtlMinutes() { return ttlMinutes; }
            public void setTtlMinutes(int ttlMinutes) { this.ttlMinutes = ttlMinutes; }
        }

        public static class Episodic {
            private int maxSummaries = 5;
            private int ttlDays = 365;

            public int getMaxSummaries() { return maxSummaries; }
            public void setMaxSummaries(int maxSummaries) { this.maxSummaries = maxSummaries; }

            public int getTtlDays() { return ttlDays; }
            public void setTtlDays(int ttlDays) { this.ttlDays = ttlDays; }
        }

        public static class Knowledge {
            private int maxSegments = 10;
            private int maxTokensPerSegment = 400;

            public int getMaxSegments() { return maxSegments; }
            public void setMaxSegments(int maxSegments) { this.maxSegments = maxSegments; }

            public int getMaxTokensPerSegment() { return maxTokensPerSegment; }
            public void setMaxTokensPerSegment(int maxTokensPerSegment) { this.maxTokensPerSegment = maxTokensPerSegment; }
        }

        public static class Composer {
            private int maxContextTokens = 3000;

            public int getMaxContextTokens() { return maxContextTokens; }
            public void setMaxContextTokens(int maxContextTokens) { this.maxContextTokens = maxContextTokens; }
        }
    }

    public static class Guardrail {
        private Input input = new Input();
        private Output output = new Output();

        public Input getInput() { return input; }
        public void setInput(Input input) { this.input = input; }

        public Output getOutput() { return output; }
        public void setOutput(Output output) { this.output = output; }

        public static class Input {
            private boolean maxLengthEnabled = true;
            private int maxLengthChars = 10000;
            private boolean promptInjectionEnabled = true;
            private boolean topicScopeEnabled = false;
            private List<String> blockedTopics = new ArrayList<>();
            private boolean piiMaskingEnabled = false;
            private boolean rateLimitEnabled = false;
            private int rateLimitMaxRequests = 60;
            private int rateLimitWindowSeconds = 60;
            private boolean rbacEnabled = true;

            public boolean isRbacEnabled() { return rbacEnabled; }
            public void setRbacEnabled(boolean rbacEnabled) { this.rbacEnabled = rbacEnabled; }

            public boolean isMaxLengthEnabled() { return maxLengthEnabled; }
            public void setMaxLengthEnabled(boolean maxLengthEnabled) { this.maxLengthEnabled = maxLengthEnabled; }

            public int getMaxLengthChars() { return maxLengthChars; }
            public void setMaxLengthChars(int maxLengthChars) { this.maxLengthChars = maxLengthChars; }

            public boolean isPromptInjectionEnabled() { return promptInjectionEnabled; }
            public void setPromptInjectionEnabled(boolean promptInjectionEnabled) { this.promptInjectionEnabled = promptInjectionEnabled; }

            public boolean isTopicScopeEnabled() { return topicScopeEnabled; }
            public void setTopicScopeEnabled(boolean topicScopeEnabled) { this.topicScopeEnabled = topicScopeEnabled; }

            public List<String> getBlockedTopics() { return blockedTopics; }
            public void setBlockedTopics(List<String> blockedTopics) { this.blockedTopics = blockedTopics; }

            public boolean isPiiMaskingEnabled() { return piiMaskingEnabled; }
            public void setPiiMaskingEnabled(boolean piiMaskingEnabled) { this.piiMaskingEnabled = piiMaskingEnabled; }

            public boolean isRateLimitEnabled() { return rateLimitEnabled; }
            public void setRateLimitEnabled(boolean rateLimitEnabled) { this.rateLimitEnabled = rateLimitEnabled; }

            public int getRateLimitMaxRequests() { return rateLimitMaxRequests; }
            public void setRateLimitMaxRequests(int rateLimitMaxRequests) { this.rateLimitMaxRequests = rateLimitMaxRequests; }

            public int getRateLimitWindowSeconds() { return rateLimitWindowSeconds; }
            public void setRateLimitWindowSeconds(int rateLimitWindowSeconds) { this.rateLimitWindowSeconds = rateLimitWindowSeconds; }
        }

        public static class Output {
            private boolean piiMaskingEnabled = false;
            private boolean disclaimerEnabled = false;
            private String disclaimerText = "This response was generated by AI.";
            private List<String> disclaimerDomains = new ArrayList<>();
            private boolean scopeValidationEnabled = false;
            private boolean schemaValidationEnabled = true;

            public boolean isPiiMaskingEnabled() { return piiMaskingEnabled; }
            public void setPiiMaskingEnabled(boolean piiMaskingEnabled) { this.piiMaskingEnabled = piiMaskingEnabled; }

            public boolean isDisclaimerEnabled() { return disclaimerEnabled; }
            public void setDisclaimerEnabled(boolean disclaimerEnabled) { this.disclaimerEnabled = disclaimerEnabled; }

            public String getDisclaimerText() { return disclaimerText; }
            public void setDisclaimerText(String disclaimerText) { this.disclaimerText = disclaimerText; }

            public List<String> getDisclaimerDomains() { return disclaimerDomains; }
            public void setDisclaimerDomains(List<String> disclaimerDomains) { this.disclaimerDomains = disclaimerDomains; }

            public boolean isScopeValidationEnabled() { return scopeValidationEnabled; }
            public void setScopeValidationEnabled(boolean scopeValidationEnabled) { this.scopeValidationEnabled = scopeValidationEnabled; }

            public boolean isSchemaValidationEnabled() { return schemaValidationEnabled; }
            public void setSchemaValidationEnabled(boolean schemaValidationEnabled) { this.schemaValidationEnabled = schemaValidationEnabled; }
        }
    }

    public static class Output {
        private int validationRetries = 2;

        public int getValidationRetries() { return validationRetries; }
        public void setValidationRetries(int validationRetries) { this.validationRetries = validationRetries; }
    }

    public static class Hitl {
        private boolean enabled = false;
        private int defaultTtlMinutes = 5;
        private boolean autoDenyOnExpiry = true;
        private boolean requireReasonOnDeny = false;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getDefaultTtlMinutes() { return defaultTtlMinutes; }
        public void setDefaultTtlMinutes(int defaultTtlMinutes) { this.defaultTtlMinutes = defaultTtlMinutes; }

        public boolean isAutoDenyOnExpiry() { return autoDenyOnExpiry; }
        public void setAutoDenyOnExpiry(boolean autoDenyOnExpiry) { this.autoDenyOnExpiry = autoDenyOnExpiry; }

        public boolean isRequireReasonOnDeny() { return requireReasonOnDeny; }
        public void setRequireReasonOnDeny(boolean requireReasonOnDeny) { this.requireReasonOnDeny = requireReasonOnDeny; }
    }

    public static class CostTracking {
        private boolean enabled = false;
        private int retentionDays = 90;
        private Map<String, Double> pricing = new HashMap<>();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getRetentionDays() { return retentionDays; }
        public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }

        public Map<String, Double> getPricing() { return pricing; }
        public void setPricing(Map<String, Double> pricing) { this.pricing = pricing; }
    }


    public static class DryRun {
        private boolean enabled = false;
        private List<String> allowedProfiles = new ArrayList<>();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public List<String> getAllowedProfiles() { return allowedProfiles; }
        public void setAllowedProfiles(List<String> allowedProfiles) { this.allowedProfiles = allowedProfiles; }
    }

    public static class ChatHistory {
        private int ttlDays = 30;
        private int maxExportSessions = 100;

        public int getTtlDays() { return ttlDays; }
        public void setTtlDays(int ttlDays) { this.ttlDays = ttlDays; }

        public int getMaxExportSessions() { return maxExportSessions; }
        public void setMaxExportSessions(int maxExportSessions) { this.maxExportSessions = maxExportSessions; }
    }

    public static class Observability {
        private boolean tracingEnabled = true;
        private boolean metricsEnabled = true;
        private boolean logTokenUsage = true;

        public boolean isTracingEnabled() { return tracingEnabled; }
        public void setTracingEnabled(boolean tracingEnabled) { this.tracingEnabled = tracingEnabled; }

        public boolean isMetricsEnabled() { return metricsEnabled; }
        public void setMetricsEnabled(boolean metricsEnabled) { this.metricsEnabled = metricsEnabled; }

        public boolean isLogTokenUsage() { return logTokenUsage; }
        public void setLogTokenUsage(boolean logTokenUsage) { this.logTokenUsage = logTokenUsage; }
    }


    public static class Enrichers {
        private boolean enabled = true;
        private int timeoutMs = 5000;
        private boolean parallel = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }

        public boolean isParallel() { return parallel; }
        public void setParallel(boolean parallel) { this.parallel = parallel; }
    }

    public static class Mcp {
        private boolean enabled = false;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static class Skillsjars {
        private boolean enabled = false;
        private String unresolvedToolsBehavior = "warn";
        private Map<String, String> toolMappings = new HashMap<>();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getUnresolvedToolsBehavior() { return unresolvedToolsBehavior; }
        public void setUnresolvedToolsBehavior(String unresolvedToolsBehavior) { this.unresolvedToolsBehavior = unresolvedToolsBehavior; }

        public Map<String, String> getToolMappings() { return toolMappings; }
        public void setToolMappings(Map<String, String> toolMappings) { this.toolMappings = toolMappings; }
    }

    public static class Audit {
        private boolean enabled = true;
        private int retentionDays = 365;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getRetentionDays() { return retentionDays; }
        public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }
    }

    /**
     * Triggers automatic session summarisation after a working-memory TTL has elapsed.
     * The scheduler scans the {@code chat_sessions} Mongo collection (populated by
     * {@code DefaultOrchestratorEngine}) and summarises sessions whose
     * {@code lastMessageAt} is older than the working-memory TTL plus a grace window.
     */
    public static class Summarization {
        private boolean enabled = true;
        private int scanIntervalMinutes = 5;
        private int graceMinutes = 1;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getScanIntervalMinutes() { return scanIntervalMinutes; }
        public void setScanIntervalMinutes(int scanIntervalMinutes) { this.scanIntervalMinutes = scanIntervalMinutes; }

        public int getGraceMinutes() { return graceMinutes; }
        public void setGraceMinutes(int graceMinutes) { this.graceMinutes = graceMinutes; }
    }

    /**
     * Configuration for the built-in chat web UI.
     * Accessible at /chat when enabled.
     */
    public static class ChatUi {
        private boolean enabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
