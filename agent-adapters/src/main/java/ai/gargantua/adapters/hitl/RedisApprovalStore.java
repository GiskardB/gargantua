package ai.gargantua.adapters.hitl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ai.gargantua.core.hitl.ApprovalDecision;
import ai.gargantua.core.hitl.ApprovalRequest;
import ai.gargantua.core.hitl.ApprovalStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class RedisApprovalStore implements ApprovalStore {

    private static final Logger log = LoggerFactory.getLogger(RedisApprovalStore.class);
    private static final String KEY_PREFIX = "hitl:approval:";
    private static final String DECISION_SUFFIX = ":decision";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisApprovalStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void savePending(String requestId, ApprovalRequest request, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(request);
            redisTemplate.opsForValue().set(KEY_PREFIX + requestId, json, ttl);
            log.debug("Saved pending approval: {}", requestId);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize approval request", e);
        }
    }

    @Override
    public Optional<ApprovalRequest> getPending(String requestId) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + requestId);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, ApprovalRequest.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize approval request: {}", requestId, e);
            return Optional.empty();
        }
    }

    @Override
    public void resolve(String requestId, ApprovalDecision decision) {
        try {
            String json = objectMapper.writeValueAsString(decision);
            redisTemplate.opsForValue().set(KEY_PREFIX + requestId + DECISION_SUFFIX, json);
            redisTemplate.delete(KEY_PREFIX + requestId);
            log.debug("Resolved approval: {} -> {}", requestId, decision.decision());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize approval decision", e);
        }
    }

    @Override
    public boolean isExpired(String requestId) {
        return Boolean.FALSE.equals(redisTemplate.hasKey(KEY_PREFIX + requestId));
    }
}
