package com.hsbc.fds.rulecheckworker.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.hsbc.fds.common.model.TransactionCheckTask;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

class AmountThresholdRuleTest {

    private final AmountThresholdRule rule = new AmountThresholdRule(new BigDecimal("10000.0"));

    @Test
    void shouldFlagAmountAboveThreshold() {
        TransactionCheckTask task = createTask("tx-001", new BigDecimal("15000.0"));

        var result = rule.check(task);

        assertThat(result).isPresent();
        assertThat(result.get().getVerdict()).isEqualTo("SUSPICIOUS");
        assertThat(result.get().getReason()).isEqualTo("AMOUNT_ABOVE_THRESHOLD");
    }

    @Test
    void shouldNotFlagAmountBelowThreshold() {
        TransactionCheckTask task = createTask("tx-002", new BigDecimal("5000.0"));

        var result = rule.check(task);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldNotFlagAmountEqualToThreshold() {
        TransactionCheckTask task = createTask("tx-003", new BigDecimal("10000.0"));

        var result = rule.check(task);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldNotFlagWhenAmountIsZero() {
        TransactionCheckTask task = createTask("tx-004", BigDecimal.ZERO);

        var result = rule.check(task);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldFlagAmountJustAboveThreshold() {
        TransactionCheckTask task = createTask("tx-005", new BigDecimal("10000.01"));

        var result = rule.check(task);

        assertThat(result).isPresent();
        assertThat(result.get().getVerdict()).isEqualTo("SUSPICIOUS");
    }

    @Test
    void shouldHandleLargeButFiniteAmount() {
        TransactionCheckTask task = createTask("tx-006", new BigDecimal("999999999999"));

        var result = rule.check(task);

        assertThat(result).isPresent();
        assertThat(result.get().getVerdict()).isEqualTo("SUSPICIOUS");
    }

    private TransactionCheckTask createTask(String txId, BigDecimal amount) {
        TransactionCheckTask task = new TransactionCheckTask();
        task.setRequestId("req-1");
        task.setTransactionId(txId);
        task.setAmount(amount);
        task.setPayeeAccountId("payee-1");
        return task;
    }
}
