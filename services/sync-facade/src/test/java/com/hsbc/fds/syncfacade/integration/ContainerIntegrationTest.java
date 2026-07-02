package com.hsbc.fds.syncfacade.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Import(com.hsbc.fds.syncfacade.TestConfig.class)
class ContainerIntegrationTest {

    private static final DockerImageName LOCALSTACK_IMAGE = DockerImageName.parse("localstack/localstack:4.3");

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(LOCALSTACK_IMAGE)
            .withServices(LocalStackContainer.Service.SQS);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.aws.credentials.access-key", () -> "test");
        registry.add("spring.cloud.aws.credentials.secret-key", () -> "test");
        registry.add("spring.cloud.aws.region.static", () -> localstack.getRegion());
        registry.add("spring.cloud.aws.sqs.endpoint", () -> localstack.getEndpoint());
        registry.add("fds.sqs.ticket-queue-url",
                () -> localstack.getEndpoint() + "/000000000000/fds-ticket-queue");
    }

    @Test
    void shouldStartLocalStackContainer() {
        assertThat(localstack.isRunning()).isTrue();
        assertThat(localstack.getEndpoint()).isNotNull();
    }

    @Test
    void shouldLoadSpringContext() {
    }
}
