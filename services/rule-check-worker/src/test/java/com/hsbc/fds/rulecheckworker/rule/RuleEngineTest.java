package com.hsbc.fds.rulecheckworker.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.hsbc.fds.rulecheckworker.model.TransactionCheckTask;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

class RuleEngineTest {

    @Test
    void shouldReturnClearWhenNoRuleMatches() {
        DenylistRule denylistRule = new DenylistRule();
        RuleEngine engine = new RuleEngine(List.of(
                new AmountThresholdRule(new BigDecimal("10000.0")), denylistRule));

        TransactionCheckTask task = createTask("tx-001", "payee-normal", new BigDecimal("100.0"));

        var result = engine.execute(task);

        assertThat(result.getVerdict()).isEqualTo("CLEAR");
    }

    @Test
    void shouldReturnDenylistMatchWhenAmountBelowThreshold() {
        AmountThresholdRule amountRule = new AmountThresholdRule(new BigDecimal("10000.0"));
        DenylistRule denylistRule = new DenylistRule();
        denylistRule.updateDenylist(Set.of("payee-99"));
        RuleEngine engine = new RuleEngine(List.of(amountRule, denylistRule));

        TransactionCheckTask task = createTask("tx-002", "payee-99", new BigDecimal("5000.0"));

        var result = engine.execute(task);

        // Only denylist matches — amount (5000) is below threshold (10000)
        assertThat(result.getVerdict()).isEqualTo("CONFIRMED_FRAUD");
    }

    @Test
    void shouldReturnAmountSuspiciousWhenOnlyAmountExceedsThreshold() {
        AmountThresholdRule amountRule = new AmountThresholdRule(new BigDecimal("1000.0"));
        DenylistRule denylistRule = new DenylistRule();
        RuleEngine engine = new RuleEngine(List.of(amountRule, denylistRule));

        TransactionCheckTask task = createTask("tx-003", "payee-normal", new BigDecimal("50000.0"));

        var result = engine.execute(task);

        // Only amount rule matches — payee is not in denylist
        assertThat(result.getVerdict()).isEqualTo("SUSPICIOUS");
    }

    @Test
    void shouldReturnMostSevereVerdictWhenBothRulesMatch() {
        // denylist (CONFIRMED_FRAUD) must win over amount threshold (SUSPICIOUS)
        AmountThresholdRule amountRule = new AmountThresholdRule(new BigDecimal("1000.0"));
        DenylistRule denylistRule = new DenylistRule();
        denylistRule.updateDenylist(Set.of("payee-99"));
        RuleEngine engine = new RuleEngine(List.of(amountRule, denylistRule));

        TransactionCheckTask task = createTask("tx-004", "payee-99", new BigDecimal("50000.0"));

        var result = engine.execute(task);

        // Both rules match, but CONFIRMED_FRAUD (denylist) is more severe than SUSPICIOUS (amount)
        assertThat(result.getVerdict()).isEqualTo("CONFIRMED_FRAUD");
        assertThat(result.getReason()).isEqualTo("PAYEE_IN_DENYLIST");
    }

    @Test
    void shouldReturnDenylistFirstWhenDenylistRuleIsFirst() {
        // Order independence: result should be the same regardless of rule list order
        DenylistRule denylistRule = new DenylistRule();
        denylistRule.updateDenylist(Set.of("payee-99"));
        AmountThresholdRule amountRule = new AmountThresholdRule(new BigDecimal("1000.0"));
        RuleEngine engine = new RuleEngine(List.of(denylistRule, amountRule));

        TransactionCheckTask task = createTask("tx-005", "payee-99", new BigDecimal("50000.0"));

        var result = engine.execute(task);

        assertThat(result.getVerdict()).isEqualTo("CONFIRMED_FRAUD");
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
