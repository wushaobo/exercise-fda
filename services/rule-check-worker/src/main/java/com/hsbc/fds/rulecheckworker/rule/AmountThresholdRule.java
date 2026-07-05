package com.hsbc.fds.rulecheckworker.rule;

import com.hsbc.fds.common.model.DetectionResult;
import com.hsbc.fds.common.model.TransactionCheckTask;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
public class AmountThresholdRule implements FraudRule {

    private final BigDecimal threshold;

    public AmountThresholdRule(@Value("${fds.rules.amount-threshold:10000.0}") BigDecimal threshold) {
        this.threshold = threshold;
    }

    @Override
    public Optional<DetectionResult> check(TransactionCheckTask task) {
        if (task.getAmount().compareTo(threshold) > 0) {
            return Optional.of(DetectionResult.suspicious(
                    task.getRequestId(),
                    task.getTransactionId(),
                    "AMOUNT_ABOVE_THRESHOLD",
                    "Amount " + task.getAmount() + " exceeds threshold " + threshold));
        }
        return Optional.empty();
    }
}
