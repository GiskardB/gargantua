package io.agentkit.adapters.cost;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class MongoCostTrackingRepository {

    private static final String COLLECTION = "token_usage";

    private final MongoTemplate mongoTemplate;

    public MongoCostTrackingRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public void save(TokenUsageDocument document) {
        mongoTemplate.insert(document, COLLECTION);
    }

    public List<Map> findSummary(Instant from, Instant to) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("timestamp").gte(from).lte(to)),
                Aggregation.group("skillName", "provider")
                        .sum("inputTokens").as("totalInputTokens")
                        .sum("outputTokens").as("totalOutputTokens")
                        .sum("estimatedCostUsd").as("totalCostUsd")
                        .count().as("requestCount")
        );
        AggregationResults<Map> results = mongoTemplate.aggregate(aggregation, COLLECTION, Map.class);
        return results.getMappedResults();
    }

    public List<TokenUsageDocument> findByUser(String userId, Instant from, Instant to) {
        Query query = new Query(
                Criteria.where("userId").is(userId)
                        .and("timestamp").gte(from).lte(to)
        );
        return mongoTemplate.find(query, TokenUsageDocument.class, COLLECTION);
    }

    public List<Map> findBySkill(Instant from, Instant to) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("timestamp").gte(from).lte(to)),
                Aggregation.group("skillName")
                        .sum("inputTokens").as("totalInputTokens")
                        .sum("outputTokens").as("totalOutputTokens")
                        .sum("estimatedCostUsd").as("totalCostUsd")
                        .count().as("requestCount")
                        .avg("durationMs").as("avgDurationMs")
        );
        AggregationResults<Map> results = mongoTemplate.aggregate(aggregation, COLLECTION, Map.class);
        return results.getMappedResults();
    }

    public List<Map> findDaily(Instant from, Instant to) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("timestamp").gte(from).lte(to)),
                Aggregation.project()
                        .and("timestamp").dateAsFormattedString("%Y-%m-%d").as("day")
                        .and("inputTokens").as("inputTokens")
                        .and("outputTokens").as("outputTokens")
                        .and("estimatedCostUsd").as("estimatedCostUsd"),
                Aggregation.group("day")
                        .sum("inputTokens").as("totalInputTokens")
                        .sum("outputTokens").as("totalOutputTokens")
                        .sum("estimatedCostUsd").as("totalCostUsd")
                        .count().as("requestCount"),
                Aggregation.sort(org.springframework.data.domain.Sort.Direction.ASC, "_id")
        );
        AggregationResults<Map> results = mongoTemplate.aggregate(aggregation, COLLECTION, Map.class);
        return results.getMappedResults();
    }

    public record TokenUsageDocument(
            String id,
            String userId,
            String sessionId,
            String skillName,
            String provider,
            String model,
            String phase,
            int inputTokens,
            int outputTokens,
            double estimatedCostUsd,
            long durationMs,
            boolean dryRun,
            Instant timestamp
    ) {
    }
}
