package com.hsbc.fds.rulecheckworker.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.hsbc.fds.rulecheckworker.model.TransactionCheckTask;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

class RuleEngineTest {

    @Test
    void shouldReturnFirstMatch() {
        AmountThresholdRule amountRule = new AmountThresholdRule(new BigDecimal("10000.0"));
        DenylistRule denylistRule = new DenylistRule();
        denylistRule.updateDenylist(Set.of("payee-99"));
        RuleEngine engine = new RuleEngine(List.of(amountRule, denylistRule));

        TransactionCheckTask task = createTask("tx-001", "payee-99", new BigDecimal("5000.0"));

        var result = engine.execute(task);

        // Both rules match, but denylist is checked second
        // Since denylist comes after amount in rule list, amount hits first only if amount > threshold
        // Here amount is 5000, so only denylist hits
        assertThat(result.getVerdict()).isEqualTo("CONFIRMED_FRAUD");
    }

    @Test
    void shouldReturnClearWhenNoRuleMatches() {
        DenylistRule denylistRule = new DenylistRule();
        RuleEngine engine = new RuleEngine(List.of(
                new AmountThresholdRule(new BigDecimal("10000.0")), denylistRule));

        TransactionCheckTask task = createTask("tx-002", "payee-normal", new BigDecimal("100.0"));

        var result = engine.execute(task);

        assertThat(result.getVerdict()).isEqualTo("CLEAR");
    }

    @Test
    void shouldReturnFirstRuleMatch() {
        AmountThresholdRule amountRule = new AmountThresholdRule(new BigDecimal("1000.0"));
        DenylistRule denylistRule = new DenylistRule();
        denylistRule.updateDenylist(Set.of("payee-99"));
        RuleEngine engine = new RuleEngine(List.of(amountRule, denylistRule));

        TransactionCheckTask task = createTask("tx-003", "payee-99", new BigDecimal("50000.0"));

        var result = engine.execute(task);

        // Both rules match, but amount runs first (SUSPICIOUS)
        assertThat(result.getVerdict()).isEqualTo("SUSPICIOUS");
    }

    private TransactionCheckTask createTask(String txId, String payee, BigDecimal amount) {
        TransactionCheckTask task = new TransactionCheckTask();
        task.setRequestId("req-1");
        task.setTransactionId(txId);
        task.setPayeeAccountId(payee);
        task.setAmount(amount);
        return task;
    }
}
