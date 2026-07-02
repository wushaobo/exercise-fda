package com.hsbc.fds.rulecheckworker.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsbc.fds.rulecheckworker.model.DetectionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@ConditionalOnProperty(name = "fds.redis.enabled", havingValue = "true", matchIfMissing = true)
public class ResultPublisher {

    private static final Logger log = LoggerFactory.getLogger(ResultPublisher.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final String channel;
    private final Duration resultTtl;

    public ResultPublisher(RedisTemplate<String, String> redisTemplate,
                           ObjectMapper objectMapper,
                           @Value("${fds.redis.result-channel:fds:results}") String channel,
                           @Value("${fds.redis.result-ttl-seconds:300}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.channel = channel;
        this.resultTtl = Duration.ofSeconds(ttlSeconds);
    }

    public void publish(DetectionResult result) {
        try {
            String json = objectMapper.writeValueAsString(result);

            // Store result with TTL (durability fallback)
            redisTemplate.opsForValue().set(result.getRequestId(), json, resultTtl);

            // Notify Facade via Pub/Sub
            redisTemplate.convertAndSend(channel, result.getRequestId());

            log.info("Result published: requestId={}, verdict={}", result.getRequestId(), result.getVerdict());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize result", e);
        }
    }
}
