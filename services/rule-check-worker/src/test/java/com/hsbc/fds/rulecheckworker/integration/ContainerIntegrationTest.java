package com.hsbc.fds.rulecheckworker.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.GenericContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Import(com.hsbc.fds.rulecheckworker.TestConfig.class)
class ContainerIntegrationTest {

    private static final DockerImageName LOCALSTACK_IMAGE = DockerImageName.parse("localstack/localstack:4.3");
    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7-alpine");

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(LOCALSTACK_IMAGE)
            .withServices(LocalStackContainer.Service.SQS);

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(REDIS_IMAGE)
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.aws.credentials.access-key", () -> "test");
        registry.add("spring.cloud.aws.credentials.secret-key", () -> "test");
        registry.add("spring.cloud.aws.region.static", () -> localstack.getRegion());
        registry.add("spring.cloud.aws.sqs.endpoint", () -> localstack.getEndpoint());
        registry.add("fds.sqs.ticket-queue-url",
                () -> localstack.getEndpoint() + "/000000000000/fds-ticket-queue");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("fds.redis.enabled", () -> "false");
    }

    @Test
    void shouldStartLocalStackContainer() {
        assertThat(localstack.isRunning()).isTrue();
        assertThat(localstack.getEndpoint()).isNotNull();
    }

    @Test
    void shouldStartRedisContainer() {
        assertThat(redis.isRunning()).isTrue();
        assertThat(redis.getMappedPort(6379)).isNotNull();
    }

    @Test
    void shouldLoadSpringContext() {
        // context loads successfully
    }
}
