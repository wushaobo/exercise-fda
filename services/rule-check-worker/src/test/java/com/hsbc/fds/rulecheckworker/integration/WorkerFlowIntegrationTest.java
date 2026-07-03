package com.hsbc.fds.rulecheckworker.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsbc.fds.rulecheckworker.model.TransactionCheckTask;
import com.hsbc.fds.rulecheckworker.redis.DenylistCache;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
@org.springframework.test.context.ActiveProfiles("integration")
class WorkerFlowIntegrationTest {

    private static final DockerImageName LOCALSTACK_IMAGE = DockerImageName.parse("localstack/localstack:4.3");
    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7-alpine");

    private static String ticketQueueUrl;

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(LOCALSTACK_IMAGE)
            .withServices(LocalStackContainer.Service.SQS);

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(REDIS_IMAGE)
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) throws Exception {
        // Create queue in LocalStack
        localstack.execInContainer("awslocal", "sqs", "create-queue",
                "--queue-name", "fds-ticket-queue",
                "--region", localstack.getRegion());
        ticketQueueUrl = localstack.getEndpoint() + "/000000000000/fds-ticket-queue";

        registry.add("spring.cloud.aws.credentials.access-key", () -> "test");
        registry.add("spring.cloud.aws.credentials.secret-key", () -> "test");
        registry.add("spring.cloud.aws.region.static", () -> localstack.getRegion());
        registry.add("spring.cloud.aws.sqs.endpoint", () -> localstack.getEndpoint());
        registry.add("fds.sqs.ticket-queue-url", () -> ticketQueueUrl);
        registry.add("fds.redis.enabled", () -> "true");
        registry.add("fds.redis.result-channel", () -> "fds:results");
        registry.add("fds.redis.result-ttl-seconds", () -> "300");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @BeforeAll
    static void logSetup() {
        System.out.println("LocalStack endpoint: " + localstack.getEndpoint());
        System.out.println("Redis host: " + redis.getHost() + ":" + redis.getMappedPort(6379));
        System.out.println("Ticket queue URL: " + ticketQueueUrl);
    }

    @Autowired
    private SqsTemplate sqsTemplate;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DenylistCache denylistCache;

    @Test
    void shouldProcessSqsMessageAndWriteResultToRedis() throws Exception {
        TransactionCheckTask task = new TransactionCheckTask();
        task.setRequestId("flow-test-req-1");
        task.setTransactionId("flow-tx-001");
        task.setPayerAccountId("payer-1");
        task.setPayeeAccountId("payee-1");
        task.setAmount(500.0);
        task.setCurrency("USD");

        String payload = objectMapper.writeValueAsString(task);
        sqsTemplate.send(to -> to.queue(ticketQueueUrl).payload(payload));

        // Worker should pick up the message, process it, and write result to Redis
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> redisTemplate.opsForValue().get("flow-test-req-1") != null);

        String resultJson = redisTemplate.opsForValue().get("flow-test-req-1");
        assertThat(resultJson).isNotNull();
        assertThat(resultJson).contains("\"verdict\":\"CLEAR\"");
        assertThat(resultJson).contains("\"reason\":\"NONE\"");
        assertThat(resultJson).contains("\"requestId\":\"flow-test-req-1\"");

        // Cleanup
        redisTemplate.delete("flow-test-req-1");
    }

    @Test
    void shouldDetectFraudulentTransaction() throws Exception {
        TransactionCheckTask task = new TransactionCheckTask();
        task.setRequestId("flow-test-req-2");
        task.setTransactionId("flow-tx-002");
        task.setPayerAccountId("payer-1");
        task.setPayeeAccountId("payee-1");
        task.setAmount(50000.0); // Above 10000 threshold
        task.setCurrency("USD");

        String payload = objectMapper.writeValueAsString(task);
        sqsTemplate.send(to -> to.queue(ticketQueueUrl).payload(payload));

        await().atMost(Duration.ofSeconds(15))
                .until(() -> redisTemplate.opsForValue().get("flow-test-req-2") != null);

        String resultJson = redisTemplate.opsForValue().get("flow-test-req-2");
        assertThat(resultJson).contains("\"verdict\":\"SUSPICIOUS\"");
        assertThat(resultJson).contains("\"reason\":\"AMOUNT_ABOVE_THRESHOLD\"");

        redisTemplate.delete("flow-test-req-2");
    }

    @Test
    void shouldFlagPayeeInDenylist() throws Exception {
        // Seed denylist and refresh cache
        redisTemplate.opsForValue().set("fds:denylist", "account-blocked-1,account-blocked-2");
        denylistCache.refresh();

        TransactionCheckTask task = new TransactionCheckTask();
        task.setRequestId("flow-test-req-3");
        task.setTransactionId("flow-tx-003");
        task.setPayerAccountId("payer-1");
        task.setPayeeAccountId("account-blocked-1");
        task.setAmount(100.0);
        task.setCurrency("USD");

        String payload = objectMapper.writeValueAsString(task);
        sqsTemplate.send(to -> to.queue(ticketQueueUrl).payload(payload));

        await().atMost(Duration.ofSeconds(15))
                .until(() -> redisTemplate.opsForValue().get("flow-test-req-3") != null);

        String resultJson = redisTemplate.opsForValue().get("flow-test-req-3");
        assertThat(resultJson).contains("\"verdict\":\"CONFIRMED_FRAUD\"");
        assertThat(resultJson).contains("\"reason\":\"PAYEE_IN_DENYLIST\"");
        assertThat(resultJson).contains("account-blocked-1");

        redisTemplate.delete("flow-test-req-3");
        redisTemplate.delete("fds:denylist");
    }
}
