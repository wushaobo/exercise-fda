package com.hsbc.fds.rulecheckworker.rule;

import com.hsbc.fds.rulecheckworker.model.DetectionResult;
import com.hsbc.fds.rulecheckworker.model.TransactionCheckTask;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AmountThresholdRule implements FraudRule {

    private final double threshold;

    public AmountThresholdRule(@Value("${fds.rules.amount-threshold:10000.0}") double threshold) {
        this.threshold = threshold;
    }

    @Override
    public Optional<DetectionResult> check(TransactionCheckTask task) {
        if (task.getAmount() > threshold) {
            return Optional.of(DetectionResult.suspicious(
                    task.getRequestId(),
                    task.getTransactionId(),
                    "AMOUNT_ABOVE_THRESHOLD",
                    "Amount " + task.getAmount() + " exceeds threshold " + threshold));
        }
        return Optional.empty();
    }
}
