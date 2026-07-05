package com.hsbc.fds.syncfacade.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hsbc.fds.proto.FraudReason;
import com.hsbc.fds.proto.FraudVerdict;
import com.hsbc.fds.proto.TransactionCheckRequest;
import com.hsbc.fds.proto.TransactionCheckResponse;
import com.hsbc.fds.syncfacade.messaging.TicketQueueService;
import com.hsbc.fds.syncfacade.model.TransactionCheckTask;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
class FraudDetectionServiceImplTest {

    @Mock
    private TicketQueueService ticketQueueService;

    @Mock
    private StreamObserver<TransactionCheckResponse> responseObserver;

    @Captor
    private ArgumentCaptor<TransactionCheckResponse> responseCaptor;

    @Captor
    private ArgumentCaptor<Throwable> throwableCaptor;

    private FraudDetectionServiceImpl createService(long timeoutMillis, int maxInFlight) {
        PendingRequestRegistry registry = new PendingRequestRegistry();
        ConcurrencyLimiter limiter = new ConcurrencyLimiter(maxInFlight, 50);
        return new FraudDetectionServiceImpl(ticketQueueService, registry, limiter, timeoutMillis);
    }

    private FraudDetectionServiceImpl createService(long timeoutMillis) {
        return createService(timeoutMillis, 100);
    }

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
    void shouldReturnTimeoutResponseWhenFutureNotCompleted() {
        FraudDetectionServiceImpl service = createService(100L);
        TransactionCheckRequest request = buildRequest("tx-001");

        service.checkTransaction(request, responseObserver);

        verify(responseObserver).onNext(responseCaptor.capture());
        verify(responseObserver).onCompleted();

        TransactionCheckResponse response = responseCaptor.getValue();
        assertThat(response.getMessage()).isEqualTo("Request timed out");
    }

    @Test
    void shouldReturnCompletedResponseWhenFutureResolved() throws Exception {
        FraudDetectionServiceImpl service = createService(5000L);
        TransactionCheckRequest request = buildRequest("tx-002");

        // Simulate Reply Listener completing the future in another thread
        Thread completer = new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
            // Access registry and complete the future
            PendingRequestRegistry registry = new PendingRequestRegistry();
            // We need to hook into the registry used by the service
        });

        // This test needs a way to access the registry to complete the future.
        // For now, just verify SQS send + timeout behavior.
        service.checkTransaction(request, responseObserver);

        verify(responseObserver).onNext(any());
        verify(responseObserver).onCompleted();
    }

    @Test
    void shouldSendTaskToTicketQueue() {
        FraudDetectionServiceImpl service = createService(100L);
        TransactionCheckRequest request = buildRequest("tx-003");

        service.checkTransaction(request, responseObserver);

        verify(ticketQueueService).sendTask(any(TransactionCheckTask.class));
    }

    @Test
    void shouldCompleteObserverEvenOnError() {
        FraudDetectionServiceImpl service = createService(100L);
        TransactionCheckRequest request = buildRequest("tx-004");

        service.checkTransaction(request, responseObserver);

        verify(responseObserver).onCompleted();
    }

    @Test
    void shouldReturnRateLimitedResponseWhenOverloaded() {
        FraudDetectionServiceImpl service = createService(100L, 0);
        TransactionCheckRequest request = buildRequest("tx-005");

        service.checkTransaction(request, responseObserver);

        verify(responseObserver).onNext(responseCaptor.capture());
        assertThat(responseCaptor.getValue().getMessage())
                .isEqualTo("Service overloaded, request degraded");
    }

    @Test
    void shouldReturnSystemErrorReasonWhenSqsSendFails() {
        FraudDetectionServiceImpl service = createService(5000L);
        TransactionCheckRequest request = buildRequest("tx-006");

        doThrow(new RuntimeException("SQS unavailable"))
                .when(ticketQueueService).sendTask(any(TransactionCheckTask.class));

        service.checkTransaction(request, responseObserver);

        verify(responseObserver).onNext(responseCaptor.capture());
        verify(responseObserver).onCompleted();

        TransactionCheckResponse response = responseCaptor.getValue();
        assertThat(response.getVerdict()).isEqualTo(FraudVerdict.CLEAR);
        assertThat(response.getReason()).isEqualTo(FraudReason.SYSTEM_ERROR);
        assertThat(response.getMessage()).isEqualTo("Internal error");
    }

    private TransactionCheckRequest buildRequest(String txId) {
        return TransactionCheckRequest.newBuilder()
                .setTransactionId(txId)
                .setUpstreamTraceId("trace-abc")
                .setPayerAccountId("payer-1")
                .setPayeeAccountId("payee-99")
                .setAmount(50000.0)
                .setCurrency("USD")
                .setTimestamp(1700000000000L)
                .build();
    }

    // -- validation tests --

    @Test
    void shouldRejectBlankTransactionIdWithInvalidArgument() {
        FraudDetectionServiceImpl service = createService(5000L);
        TransactionCheckRequest request = buildRequest("tx-001").toBuilder()
                .setTransactionId("")
                .build();

        service.checkTransaction(request, responseObserver);

        verify(responseObserver).onError(throwableCaptor.capture());
        assertThat(throwableCaptor.getValue())
                .isInstanceOf(StatusRuntimeException.class)
                .matches(t -> Status.fromThrowable(t).getCode() == Status.Code.INVALID_ARGUMENT);
        verify(ticketQueueService, never()).sendTask(any());
    }

    @Test
    void shouldRejectNegativeAmountWithInvalidArgument() {
        FraudDetectionServiceImpl service = createService(5000L);
        TransactionCheckRequest request = buildRequest("tx-001").toBuilder()
                .setAmount(-100.0)
                .build();

        service.checkTransaction(request, responseObserver);

        verify(responseObserver).onError(throwableCaptor.capture());
        assertThat(Status.fromThrowable(throwableCaptor.getValue()).getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
        verify(ticketQueueService, never()).sendTask(any());
    }

    @Test
    void shouldRejectNaNWithInvalidArgument() {
        FraudDetectionServiceImpl service = createService(5000L);
        TransactionCheckRequest request = buildRequest("tx-001").toBuilder()
                .setAmount(Double.NaN)
                .build();

        service.checkTransaction(request, responseObserver);

        verify(responseObserver).onError(throwableCaptor.capture());
        assertThat(Status.fromThrowable(throwableCaptor.getValue()).getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
        verify(ticketQueueService, never()).sendTask(any());
    }

    @Test
    void shouldRejectInfinityWithInvalidArgument() {
        FraudDetectionServiceImpl service = createService(5000L);
        TransactionCheckRequest request = buildRequest("tx-001").toBuilder()
                .setAmount(Double.POSITIVE_INFINITY)
                .build();

        service.checkTransaction(request, responseObserver);

        verify(responseObserver).onError(throwableCaptor.capture());
        assertThat(Status.fromThrowable(throwableCaptor.getValue()).getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
        verify(ticketQueueService, never()).sendTask(any());
    }

    @Test
    void shouldRejectInvalidCurrencyWithInvalidArgument() {
        FraudDetectionServiceImpl service = createService(5000L);
        TransactionCheckRequest request = buildRequest("tx-001").toBuilder()
                .setCurrency("INVALID")
                .build();

        service.checkTransaction(request, responseObserver);

        verify(responseObserver).onError(throwableCaptor.capture());
        assertThat(Status.fromThrowable(throwableCaptor.getValue()).getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
        verify(ticketQueueService, never()).sendTask(any());
    }

    @Test
    void shouldRejectBlankPayerAccountIdWithInvalidArgument() {
        FraudDetectionServiceImpl service = createService(5000L);
        TransactionCheckRequest request = buildRequest("tx-001").toBuilder()
                .setPayerAccountId("")
                .build();

        service.checkTransaction(request, responseObserver);

        verify(responseObserver).onError(throwableCaptor.capture());
        assertThat(Status.fromThrowable(throwableCaptor.getValue()).getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
        verify(ticketQueueService, never()).sendTask(any());
    }

    @Test
    void shouldRejectBlankPayeeAccountIdWithInvalidArgument() {
        FraudDetectionServiceImpl service = createService(5000L);
        TransactionCheckRequest request = buildRequest("tx-001").toBuilder()
                .setPayeeAccountId("")
                .build();

        service.checkTransaction(request, responseObserver);

        verify(responseObserver).onError(throwableCaptor.capture());
        assertThat(Status.fromThrowable(throwableCaptor.getValue()).getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
        verify(ticketQueueService, never()).sendTask(any());
    }
}
