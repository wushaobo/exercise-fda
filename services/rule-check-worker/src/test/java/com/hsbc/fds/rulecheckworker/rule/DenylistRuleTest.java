package com.hsbc.fds.rulecheckworker.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.hsbc.fds.common.model.TransactionCheckTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

class DenylistRuleTest {

    private final DenylistRule rule = new DenylistRule();

    @BeforeEach
    void setUp() {
        rule.updateDenylist(Set.of("account-black-1", "account-black-2"));
    }

    @Test
    void shouldFlagPayeeInDenylist() {
        TransactionCheckTask task = createTask("tx-001", "account-black-1");

        var result = rule.check(task);

        assertThat(result).isPresent();
        assertThat(result.get().getVerdict()).isEqualTo("CONFIRMED_FRAUD");
        assertThat(result.get().getReason()).isEqualTo("PAYEE_IN_DENYLIST");
    }

    @Test
    void shouldNotFlagPayeeNotInDenylist() {
        TransactionCheckTask task = createTask("tx-002", "account-normal");

        var result = rule.check(task);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldMatchCaseInsensitively() {
        TransactionCheckTask task = createTask("tx-003", "ACCOUNT-BLACK-1");

        var result = rule.check(task);

        assertThat(result).isPresent();
        assertThat(result.get().getReason()).isEqualTo("PAYEE_IN_DENYLIST");
    }

    @Test
    void shouldUpdateDenylist() {
        rule.updateDenylist(Set.of("new-account"));

        TransactionCheckTask task = createTask("tx-003", "account-black-1");
        assertThat(rule.check(task)).isEmpty();

        TransactionCheckTask task2 = createTask("tx-004", "new-account");
        assertThat(rule.check(task2)).isPresent();
    }

    private TransactionCheckTask createTask(String txId, String payeeAccountId) {
        TransactionCheckTask task = new TransactionCheckTask();
        task.setRequestId("req-1");
        task.setTransactionId(txId);
        task.setPayeeAccountId(payeeAccountId);
        return task;
    }
}
