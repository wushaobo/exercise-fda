package com.hsbc.fds.syncfacade.integration;

import com.hsbc.fds.syncfacade.messaging.TicketQueueService;
import com.hsbc.fds.syncfacade.model.TransactionCheckTask;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
@Testcontainers
class SqsIntegrationTest {

    private static final DockerImageName LOCALSTACK_IMAGE = DockerImageName.parse("localstack/localstack:4.3");

    private static String ticketQueueUrl;

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(LOCALSTACK_IMAGE)
            .withServices(LocalStackContainer.Service.SQS);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) throws Exception {
        localstack.execInContainer("awslocal", "sqs", "create-queue",
                "--queue-name", "fds-ticket-queue",
                "--region", localstack.getRegion());
        ticketQueueUrl = localstack.getEndpoint() + "/000000000000/fds-ticket-queue";

        registry.add("spring.cloud.aws.credentials.access-key", () -> "test");
        registry.add("spring.cloud.aws.credentials.secret-key", () -> "test");
        registry.add("spring.cloud.aws.region.static", () -> localstack.getRegion());
        registry.add("spring.cloud.aws.sqs.endpoint", () -> localstack.getEndpoint());
        registry.add("fds.sqs.ticket-queue-url", () -> ticketQueueUrl);
    }

    @Autowired
    private TicketQueueService ticketQueueService;

    @Test
    void shouldSendTaskToLocalStackSqsWithoutError() {
        TransactionCheckTask task = new TransactionCheckTask(
                "req-int-1", "tx-001", "payer-1", "payee-99",
                new BigDecimal("50000.0"), "USD", 1700000000000L);

        assertThatCode(() -> ticketQueueService.sendTask(task))
                .doesNotThrowAnyException();
    }
}
