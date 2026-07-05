package com.hsbc.fds.syncfacade.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsbc.fds.syncfacade.model.TransactionCheckTask;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

@ExtendWith(MockitoExtension.class)
class TicketQueueServiceTest {

    @Mock
    private SqsTemplate sqsTemplate;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private TicketQueueService ticketQueueService;

    @Captor
    private ArgumentCaptor<String> payloadCaptor;

    @Test
    void shouldSendSerializedTaskToSqs() {
        TransactionCheckTask task = new TransactionCheckTask(
                "req-123", "tx-001", "payer-1", "payee-99",
                new BigDecimal("50000.0"), "USD", 1700000000000L);

        ticketQueueService.sendTask(task);

        verify(sqsTemplate).send(any());
    }

    @Test
    void shouldSendValidJsonPayload() {
        TransactionCheckTask task = new TransactionCheckTask(
                "req-123", "tx-001", "payer-1", "payee-99",
                new BigDecimal("50000.0"), "USD", 1700000000000L);

        ticketQueueService.sendTask(task);

        verify(sqsTemplate).send(any());
    }
}
