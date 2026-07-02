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
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class FraudDetectionServiceImpl extends FraudDetectionServiceGrpc.FraudDetectionServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(FraudDetectionServiceImpl.class);
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int SUFFIX_LENGTH = 4;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TicketQueueService ticketQueueService;

    public FraudDetectionServiceImpl(TicketQueueService ticketQueueService) {
        this.ticketQueueService = ticketQueueService;
    }

    @Override
    public void checkTransaction(TransactionCheckRequest request,
                                 StreamObserver<TransactionCheckResponse> responseObserver) {
        String requestId = generateRequestId(request.getUpstreamTraceId());

        log.info("Received check request, requestId={}, transactionId={}, amount={}",
                requestId, request.getTransactionId(), request.getAmount());

        TransactionCheckTask task = new TransactionCheckTask(
                requestId,
                request.getTransactionId(),
                request.getPayerAccountId(),
                request.getPayeeAccountId(),
                request.getAmount(),
                request.getCurrency()
        );

        ticketQueueService.sendTask(task);

        // TODO: PendingRequestRegistry + Future will be added in next task
        TransactionCheckResponse response = TransactionCheckResponse.newBuilder()
                .setTransactionId(request.getTransactionId())
                .setVerdict(FraudVerdict.CLEAR)
                .setReason(FraudReason.NONE)
                .setMessage("Request accepted, pending processing")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    static String generateRequestId(String upstreamTraceId) {
        StringBuilder suffix = new StringBuilder(SUFFIX_LENGTH);
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            suffix.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return upstreamTraceId + "-" + suffix;
    }
}
