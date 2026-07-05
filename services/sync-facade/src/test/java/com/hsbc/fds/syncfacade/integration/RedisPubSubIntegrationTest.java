package com.hsbc.fds.syncfacade.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsbc.fds.proto.FraudReason;
import com.hsbc.fds.proto.FraudVerdict;
import com.hsbc.fds.proto.TransactionCheckResponse;
import com.hsbc.fds.syncfacade.grpc.PendingRequestRegistry;
import com.hsbc.fds.common.model.DetectionResult;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration")
@Import(RedisPubSubIntegrationTest.MockSqsConfig.class)
class RedisPubSubIntegrationTest {

    @TestConfiguration
    static class MockSqsConfig {
        @Bean
        @Primary
        public SqsTemplate sqsTemplate() {
            return mock(SqsTemplate.class);
        }
    }

    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(REDIS_IMAGE)
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private PendingRequestRegistry registry;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCompleteFutureWhenPubSubMessageReceived() throws Exception {
        String requestId = "pubsub-test-req-1";
        String transactionId = "tx-pubsub-001";

        CompletableFuture<TransactionCheckResponse> future = new CompletableFuture<>();
        registry.register(requestId, future);

        DetectionResult result = new DetectionResult();
        result.setRequestId(requestId);
        result.setTransactionId(transactionId);
        result.setVerdict("SUSPICIOUS");
        result.setReason("AMOUNT_ABOVE_THRESHOLD");
        result.setMessage("Amount exceeds threshold");

        String resultJson = objectMapper.writeValueAsString(result);
        redisTemplate.opsForValue().set(requestId, resultJson, Duration.ofSeconds(60));
        redisTemplate.convertAndSend("fds:results", requestId);

        TransactionCheckResponse response = future.get(10, TimeUnit.SECONDS);

        assertThat(response.getVerdict()).isEqualTo(FraudVerdict.SUSPICIOUS);
        assertThat(response.getReason()).isEqualTo(FraudReason.AMOUNT_ABOVE_THRESHOLD);
        assertThat(response.getMessage()).isEqualTo("Amount exceeds threshold");
        assertThat(response.getTransactionId()).isEqualTo(transactionId);

        redisTemplate.delete(requestId);
    }

    @Test
    void shouldCompleteFutureWithClearVerdict() throws Exception {
        String requestId = "pubsub-test-req-2";

        CompletableFuture<TransactionCheckResponse> future = new CompletableFuture<>();
        registry.register(requestId, future);

        DetectionResult result = new DetectionResult();
        result.setRequestId(requestId);
        result.setTransactionId("tx-clear");
        result.setVerdict("CLEAR");
        result.setReason("NONE");
        result.setMessage("No fraud detected");

        redisTemplate.opsForValue().set(requestId, objectMapper.writeValueAsString(result), Duration.ofSeconds(60));
        redisTemplate.convertAndSend("fds:results", requestId);

        TransactionCheckResponse response = future.get(10, TimeUnit.SECONDS);

        assertThat(response.getVerdict()).isEqualTo(FraudVerdict.CLEAR);
        assertThat(response.getReason()).isEqualTo(FraudReason.NONE);

        redisTemplate.delete(requestId);
    }
}
