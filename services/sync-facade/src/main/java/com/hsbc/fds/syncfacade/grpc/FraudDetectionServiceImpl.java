package com.hsbc.fds.syncfacade.grpc;

import com.hsbc.fds.proto.FraudDetectionServiceGrpc;
import com.hsbc.fds.proto.FraudReason;
import com.hsbc.fds.proto.FraudVerdict;
import com.hsbc.fds.proto.TransactionCheckRequest;
import com.hsbc.fds.proto.TransactionCheckResponse;
import com.hsbc.fds.syncfacade.messaging.TicketQueueService;
import com.hsbc.fds.syncfacade.model.TransactionCheckTask;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Currency;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class FraudDetectionServiceImpl extends FraudDetectionServiceGrpc.FraudDetectionServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(FraudDetectionServiceImpl.class);
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int SUFFIX_LENGTH = 4;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000000");

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

        // Validate input before proceeding
        String validationError = validateRequest(request);
        if (validationError != null) {
            log.warn("Validation failed, transactionId={}, error={}",
                    request.getTransactionId(), validationError);
            limiter.release();
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription(validationError)
                            .asRuntimeException());
            return;
        }

        String requestId = generateRequestId(request.getUpstreamTraceId());

        log.info("Received check request, requestId={}, transactionId={}, amount={}",
                requestId, request.getTransactionId(), request.getAmount());

        CompletableFuture<TransactionCheckResponse> future = new CompletableFuture<>();
        if (!registry.register(requestId, future)) {
            log.error("Duplicate requestId detected, requestId={}, transactionId={}",
                    requestId, request.getTransactionId());
            limiter.release();
            responseObserver.onNext(buildErrorResponse(request.getTransactionId()));
            responseObserver.onCompleted();
            return;
        }

        try {
            BigDecimal amount = BigDecimal.valueOf(request.getAmount());
            TransactionCheckTask task = new TransactionCheckTask(
                    requestId,
                    request.getTransactionId(),
                    request.getPayerAccountId(),
                    request.getPayeeAccountId(),
                    amount,
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

    /**
     * Validates the gRPC request fields before DTO construction.
     * Returns the first validation error message, or null if valid.
     */
    private String validateRequest(TransactionCheckRequest request) {
        // transactionId
        if (isBlank(request.getTransactionId())) {
            return "transactionId must not be blank";
        }
        // payerAccountId
        if (isBlank(request.getPayerAccountId())) {
            return "payerAccountId must not be blank";
        }
        // payeeAccountId
        if (isBlank(request.getPayeeAccountId())) {
            return "payeeAccountId must not be blank";
        }

        // amount
        double protoAmount = request.getAmount();
        if (Double.isNaN(protoAmount)) {
            return "amount must not be NaN";
        }
        if (Double.isInfinite(protoAmount)) {
            return "amount must be finite";
        }
        if (protoAmount < 0) {
            return "amount must be non-negative";
        }
        BigDecimal amount = BigDecimal.valueOf(protoAmount);
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            return "amount must not exceed " + MAX_AMOUNT;
        }

        // currency
        if (isBlank(request.getCurrency())) {
            return "currency must not be blank";
        }
        String currency = request.getCurrency().trim();
        if (currency.length() != 3) {
            return "currency must be a 3-letter ISO 4217 code";
        }
        if (!currency.equals(currency.toUpperCase())) {
            return "currency must be uppercase";
        }
        try {
            Currency.getInstance(currency);
        } catch (IllegalArgumentException e) {
            return "currency must be a valid ISO 4217 code";
        }

        // timestamp
        if (request.getTimestamp() < 0) {
            return "timestamp must not be negative";
        }

        return null;
    }

    static String generateRequestId(String upstreamTraceId) {
        StringBuilder suffix = new StringBuilder(SUFFIX_LENGTH);
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            suffix.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return upstreamTraceId + "-" + suffix;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
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
