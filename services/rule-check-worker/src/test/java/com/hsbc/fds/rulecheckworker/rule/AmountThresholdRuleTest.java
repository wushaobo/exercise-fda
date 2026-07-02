package com.hsbc.fds.rulecheckworker.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.hsbc.fds.rulecheckworker.model.TransactionCheckTask;
import org.junit.jupiter.api.Test;

class AmountThresholdRuleTest {

    private final AmountThresholdRule rule = new AmountThresholdRule(10000.0);

    @Test
    void shouldFlagAmountAboveThreshold() {
        TransactionCheckTask task = createTask("tx-001", 15000.0);

        var result = rule.check(task);

        assertThat(result).isPresent();
        assertThat(result.get().getVerdict()).isEqualTo("SUSPICIOUS");
        assertThat(result.get().getReason()).isEqualTo("AMOUNT_ABOVE_THRESHOLD");
    }

    @Test
    void shouldNotFlagAmountBelowThreshold() {
        TransactionCheckTask task = createTask("tx-002", 5000.0);

        var result = rule.check(task);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldNotFlagAmountEqualToThreshold() {
        TransactionCheckTask task = createTask("tx-003", 10000.0);

        var result = rule.check(task);

        assertThat(result).isEmpty();
    }

    private TransactionCheckTask createTask(String txId, double amount) {
        TransactionCheckTask task = new TransactionCheckTask();
        task.setRequestId("req-1");
        task.setTransactionId(txId);
        task.setAmount(amount);
        task.setPayeeAccountId("payee-1");
        return task;
    }
}
