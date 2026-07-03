package com.hsbc.fds.e2e;

import com.hsbc.fds.proto.FraudDetectionServiceGrpc;
import com.hsbc.fds.proto.FraudReason;
import com.hsbc.fds.proto.FraudVerdict;
import com.hsbc.fds.proto.TransactionCheckRequest;
import com.hsbc.fds.proto.TransactionCheckResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.concurrent.TimeUnit;

/**
 * Simple e2e gRPC client. Connects to sync-facade and verifies the full
 * gRPC → SQS → Worker → Redis → gRPC response pipeline.
 */
public class E2eClient {

    private final FraudDetectionServiceGrpc.FraudDetectionServiceBlockingStub stub;
    private int passed;
    private int failed;

    public E2eClient(String host, int port) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.stub = FraudDetectionServiceGrpc.newBlockingStub(channel);
    }

    public int run() {
        testNormalTransaction();
        testHighAmountSuspicious();
        testKnownFraudPayee();
        summary();
        return failed > 0 ? 1 : 0;
    }

    void testNormalTransaction() {
        var req = TransactionCheckRequest.newBuilder()
                .setTransactionId("e2e-tx-normal")
                .setUpstreamTraceId("e2e-trace-1")
                .setPayerAccountId("payer-1")
                .setPayeeAccountId("payee-1")
                .setAmount(100.0)
                .setCurrency("USD")
                .build();

        TransactionCheckResponse resp = stub.withDeadlineAfter(10, TimeUnit.SECONDS)
                .checkTransaction(req);

        check(resp.getVerdict() == FraudVerdict.CLEAR,
                "Normal transaction should be CLEAR, got " + resp.getVerdict());

        check(resp.getTransactionId().equals("e2e-tx-normal"),
                "Transaction ID should match, got " + resp.getTransactionId());
    }

    void testHighAmountSuspicious() {
        var req = TransactionCheckRequest.newBuilder()
                .setTransactionId("e2e-tx-high")
                .setUpstreamTraceId("e2e-trace-2")
                .setPayerAccountId("payer-1")
                .setPayeeAccountId("payee-2")
                .setAmount(50000.0)
                .setCurrency("USD")
                .build();

        TransactionCheckResponse resp = stub.withDeadlineAfter(10, TimeUnit.SECONDS)
                .checkTransaction(req);

        check(resp.getVerdict() == FraudVerdict.SUSPICIOUS,
                "High amount should be SUSPICIOUS, got " + resp.getVerdict());

        check(resp.getReason() == FraudReason.AMOUNT_ABOVE_THRESHOLD,
                "Reason should be AMOUNT_ABOVE_THRESHOLD, got " + resp.getReason());
    }

    void testKnownFraudPayee() {
        var req = TransactionCheckRequest.newBuilder()
                .setTransactionId("e2e-tx-fraud")
                .setUpstreamTraceId("e2e-trace-3")
                .setPayerAccountId("payer-1")
                .setPayeeAccountId("account-blocked-1")
                .setAmount(5000.0)
                .setCurrency("USD")
                .build();

        TransactionCheckResponse resp = stub.withDeadlineAfter(10, TimeUnit.SECONDS)
                .checkTransaction(req);

        check(resp.getVerdict() == FraudVerdict.CONFIRMED_FRAUD,
                "Blocked payee should be CONFIRMED_FRAUD, got " + resp.getVerdict());

        check(resp.getReason() == FraudReason.PAYEE_IN_DENYLIST,
                "Reason should be PAYEE_IN_DENYLIST, got " + resp.getReason());
    }

    void check(boolean condition, String msg) {
        if (condition) {
            System.out.println("  PASS: " + msg);
            passed++;
        } else {
            System.out.println("  FAIL: " + msg);
            failed++;
        }
    }

    void summary() {
        int total = passed + failed;
        System.out.println();
        System.out.println("=== E2E Results: " + passed + "/" + total + " passed ===");
    }

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 9090;
        System.exit(new E2eClient(host, port).run());
    }
}
