package com.hsbc.fds.syncfacade.grpc;

import com.hsbc.fds.proto.FraudDetectionServiceGrpc;
import com.hsbc.fds.proto.FraudReason;
import com.hsbc.fds.proto.FraudVerdict;
import com.hsbc.fds.proto.TransactionCheckRequest;
import com.hsbc.fds.proto.TransactionCheckResponse;
import com.hsbc.fds.syncfacade.messaging.TicketQueueService;
import com.hsbc.fds.syncfacade.model.TransactionCheckTask;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class FraudDetectionServiceImpl extends FraudDetectionServiceGrpc.FraudDetectionServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(FraudDetectionServiceImpl.class);
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int SUFFIX_LENGTH = 4;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TicketQueueService ticketQueueService;
    private final PendingRequestRegistry registry;
    private final ConcurrencyLimiter limiter;
    private final long timeoutMillis;

    public FraudDetectionServiceImpl(TicketQueueService ticketQueueService,
                                     PendingRequestRegistry registry,
                                     ConcurrencyLimiter limiter,
                                     @Value("${fds.request.timeout-millis:500}") long timeoutMillis) {
        this.ticketQueueService = ticketQueueService;
        this.registry = registry;
        this.limiter = limiter;
        this.timeoutMillis = timeoutMillis;
    }

    @Override
    public void checkTransaction(TransactionCheckRequest request,
                                 StreamObserver<TransactionCheckResponse> responseObserver) {
        if (!limiter.tryAcquire()) {
            responseObserver.onNext(buildRateLimitedResponse(request.getTransactionId()));
            responseObserver.onCompleted();
            return;
        }

        String requestId = generateRequestId(request.getUpstreamTraceId());

        log.info("Received check request, requestId={}, transactionId={}, amount={}",
                requestId, request.getTransactionId(), request.getAmount());

        CompletableFuture<TransactionCheckResponse> future = new CompletableFuture<>();
        registry.register(requestId, future);

        try {
            TransactionCheckTask task = new TransactionCheckTask(
                    requestId,
                    request.getTransactionId(),
                    request.getPayerAccountId(),
                    request.getPayeeAccountId(),
                    BigDecimal.valueOf(request.getAmount()),
                    request.getCurrency(),
                    request.getTimestamp()
            );
            ticketQueueService.sendTask(task);

            TransactionCheckResponse response = future.get(timeoutMillis, TimeUnit.MILLISECONDS);
            responseObserver.onNext(response);
        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("Request timed out, requestId={}", requestId);
            responseObserver.onNext(buildTimeoutResponse(request.getTransactionId()));
        } catch (Exception e) {
            log.error("Unexpected error, requestId={}", requestId, e);
            responseObserver.onNext(buildErrorResponse(request.getTransactionId()));
        } finally {
            registry.remove(requestId);
            limiter.release();
            responseObserver.onCompleted();
        }
    }

    static String generateRequestId(String upstreamTraceId) {
        StringBuilder suffix = new StringBuilder(SUFFIX_LENGTH);
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            suffix.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return upstreamTraceId + "-" + suffix;
    }

    private TransactionCheckResponse buildTimeoutResponse(String transactionId) {
        return TransactionCheckResponse.newBuilder()
                .setTransactionId(transactionId)
                .setVerdict(FraudVerdict.CLEAR)
                .setReason(FraudReason.SYSTEM_ERROR)
                .setMessage("Request timed out")
                .build();
    }

    private TransactionCheckResponse buildErrorResponse(String transactionId) {
        return TransactionCheckResponse.newBuilder()
                .setTransactionId(transactionId)
                .setVerdict(FraudVerdict.CLEAR)
                .setReason(FraudReason.SYSTEM_ERROR)
                .setMessage("Internal error")
                .build();
    }

    private TransactionCheckResponse buildRateLimitedResponse(String transactionId) {
        return TransactionCheckResponse.newBuilder()
                .setTransactionId(transactionId)
                .setVerdict(FraudVerdict.CLEAR)
                .setReason(FraudReason.SYSTEM_ERROR)
                .setMessage("Service overloaded, request degraded")
                .build();
    }
}
