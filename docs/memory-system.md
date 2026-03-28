# Memory System

## Architecture -- Three Layers

```
┌─────────────────────────────────────────────┐
│         Working Memory (Redis)              │  Session-scoped, TTL-based
│         Last N messages of current chat     │  Default: 20 msgs, 30 min TTL
├─────────────────────────────────────────────┤
│         Episodic Memory (MongoDB)           │  Cross-session summaries
│         LLM-compressed past sessions        │  Default: 5 most recent, 365 day TTL
├─────────────────────────────────────────────┤
│         Knowledge Memory (MongoDB)          │  Persistent user profile
│         User preferences, profile data      │  Segment-based (key-value)
└─────────────────────────────────────────────┘
```

### Working Memory (Redis)

- Key: `working_memory:{sessionId}`
- TTL resets on each message
- LTRIM enforces max messages
- When TTL expires, triggers SessionSummarizer

### Episodic Memory (MongoDB)

- Collection: `session_summaries`
- Auto-generated when working memory expires
- Fields: userId, sessionId, summary, keyTopics, unresolvedItems, messageCount
- Sorted by date desc, limited by maxSummaries

### Knowledge Memory (MongoDB)

- Collection: `user_knowledge`
- Persistent user profile segments (e.g., "preferences", "financial_profile")
- Upsert by userId + segmentKey

## Memory Composer

Fetches all 3 layers in parallel via `CompletableFuture.allOf()`. Respects token budget with priority truncation:

1. **Working messages** (highest priority -- never truncated)
2. **Episodic summaries** (truncated from oldest)
3. **Knowledge segments** (truncated last)

## Configuration

```yaml
gargantua:
  memory:
    working:
      max-messages: 20
      ttl-minutes: 30
    episodic:
      max-summaries: 5
      ttl-days: 365
    knowledge:
      max-segments: 10
      max-tokens-per-segment: 400
    composer:
      max-context-tokens: 3000
```

## Agent Memory SDK -- Standalone Library

The memory layer is a standalone Maven artifact (`ai.gargantua:agent-memory-sdk`) usable in any Spring Boot project:

```xml
<dependency>
    <groupId>ai.gargantua</groupId>
    <artifactId>agent-memory-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

All beans use `@ConditionalOnMissingBean` -- override any adapter by declaring your own bean:

```java
@Bean
public WorkingMemoryPort workingMemory() {
    return new MyCustomWorkingMemory();
}
```

## In-Memory Stubs for Testing

Available in test scope: `InMemoryWorkingMemoryAdapter`, `InMemoryEpisodicMemoryAdapter`, `InMemoryKnowledgeMemoryAdapter`. Use these instead of Mockito for realistic unit tests.
