package com.hsbc.fds.syncfacade.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.hsbc.fds.proto.FraudVerdict;
import com.hsbc.fds.proto.TransactionCheckRequest;
import com.hsbc.fds.syncfacade.messaging.TicketQueueService;
import com.hsbc.fds.syncfacade.model.TransactionCheckTask;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FraudDetectionServiceImplTest {

    @Mock
    private TicketQueueService ticketQueueService;

    @Mock
    private StreamObserver<com.hsbc.fds.proto.TransactionCheckResponse> responseObserver;

    @InjectMocks
    private FraudDetectionServiceImpl service;

    @Captor
    private ArgumentCaptor<TransactionCheckTask> taskCaptor;

    @Test
    void shouldGenerateRequestIdWithTraceIdPrefix() {
        String requestId = FraudDetectionServiceImpl.generateRequestId("trace-abc123");

        assertThat(requestId).startsWith("trace-abc123-");
        assertThat(requestId).hasSize("trace-abc123-".length() + 4);
    }

    @Test
    void shouldGenerateUniqueRequestIds() {
        String id1 = FraudDetectionServiceImpl.generateRequestId("trace-1");
        String id2 = FraudDetectionServiceImpl.generateRequestId("trace-1");

        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void shouldSendTaskToTicketQueueOnCheck() {
        TransactionCheckRequest request = TransactionCheckRequest.newBuilder()
                .setTransactionId("tx-001")
                .setUpstreamTraceId("trace-abc")
                .setPayerAccountId("payer-1")
                .setPayeeAccountId("payee-99")
                .setAmount(50000.0)
                .setCurrency("USD")
                .build();

        service.checkTransaction(request, responseObserver);

        verify(ticketQueueService).sendTask(taskCaptor.capture());
        TransactionCheckTask task = taskCaptor.getValue();

        assertThat(task.getTransactionId()).isEqualTo("tx-001");
        assertThat(task.getPayerAccountId()).isEqualTo("payer-1");
        assertThat(task.getPayeeAccountId()).isEqualTo("payee-99");
        assertThat(task.getAmount()).isEqualTo(50000.0);
        assertThat(task.getCurrency()).isEqualTo("USD");
        assertThat(task.getRequestId()).startsWith("trace-abc-");
    }

    @Test
    void shouldReturnAcceptedResponse() {
        TransactionCheckRequest request = TransactionCheckRequest.newBuilder()
                .setTransactionId("tx-001")
                .setUpstreamTraceId("trace-abc")
                .setPayerAccountId("payer-1")
                .setPayeeAccountId("payee-99")
                .setAmount(100.0)
                .setCurrency("USD")
                .build();

        service.checkTransaction(request, responseObserver);

        verify(responseObserver).onNext(any());
        verify(responseObserver).onCompleted();
    }
}
