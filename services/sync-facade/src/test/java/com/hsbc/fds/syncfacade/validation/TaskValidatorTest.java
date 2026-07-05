package com.hsbc.fds.syncfacade.validation;

import com.hsbc.fds.syncfacade.model.TransactionCheckTask;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TaskValidatorTest {

    private static TransactionCheckTask validTask() {
        TransactionCheckTask task = new TransactionCheckTask();
        task.setRequestId("req-001");
        task.setTransactionId("tx-001");
        task.setPayerAccountId("payer-1");
        task.setPayeeAccountId("payee-99");
        task.setAmount(new BigDecimal("50000.0"));
        task.setCurrency("USD");
        task.setTimestamp(1700000000000L);
        return task;
    }

    @Test
    void shouldReturnEmptyErrorsForValidTask() {
        List<String> errors = TaskValidator.validate(validTask());
        assertThat(errors).isEmpty();
    }

    @Test
    void shouldReturnTrueForValidTask() {
        assertThat(TaskValidator.isValid(validTask())).isTrue();
    }

    // -- transactionId --

    @Test
    void shouldRejectNullTransactionId() {
        TransactionCheckTask task = validTask();
        task.setTransactionId(null);
        List<String> errors = TaskValidator.validate(task);
        assertThat(errors).anyMatch(e -> e.contains("transactionId"));
    }

    @Test
    void shouldRejectEmptyTransactionId() {
        TransactionCheckTask task = validTask();
        task.setTransactionId("");
        List<String> errors = TaskValidator.validate(task);
        assertThat(errors).anyMatch(e -> e.contains("transactionId"));
    }

    @Test
    void shouldRejectBlankTransactionId() {
        TransactionCheckTask task = validTask();
        task.setTransactionId("   ");
        List<String> errors = TaskValidator.validate(task);
        assertThat(errors).anyMatch(e -> e.contains("transactionId"));
    }

    // -- payerAccountId --

    @Test
    void shouldRejectNullPayerAccountId() {
        TransactionCheckTask task = validTask();
        task.setPayerAccountId(null);
        List<String> errors = TaskValidator.validate(task);
        assertThat(errors).anyMatch(e -> e.contains("payerAccountId"));
    }

    @Test
    void shouldRejectEmptyPayerAccountId() {
        TransactionCheckTask task = validTask();
        task.setPayerAccountId("");
        List<String> errors = TaskValidator.validate(task);
        assertThat(errors).anyMatch(e -> e.contains("payerAccountId"));
    }

    @Test
    void shouldRejectBlankPayerAccountId() {
        TransactionCheckTask task = validTask();
        task.setPayerAccountId("   ");
        List<String> errors = TaskValidator.validate(task);
        assertThat(errors).anyMatch(e -> e.contains("payerAccountId"));
    }

    // -- payeeAccountId --

    @Test
    void shouldRejectNullPayeeAccountId() {
        TransactionCheckTask task = validTask();
        task.setPayeeAccountId(null);
        List<String> errors = TaskValidator.validate(task);
        assertThat(errors).anyMatch(e -> e.contains("payeeAccountId"));
    }

    @Test
    void shouldRejectEmptyPayeeAccountId() {
        TransactionCheckTask task = validTask();
        task.setPayeeAccountId("");
        List<String> errors = TaskValidator.validate(task);
        assertThat(errors).anyMatch(e -> e.contains("payeeAccountId"));
    }

    @Test
    void shouldRejectBlankPayeeAccountId() {
        TransactionCheckTask task = validTask();
        task.setPayeeAccountId("   ");
        List<String> errors = TaskValidator.validate(task);
        assertThat(errors).anyMatch(e -> e.contains("payeeAccountId"));
    }

    // -- amount --

    @Test
    void shouldRejectNullAmount() {
        TransactionCheckTask task = validTask();
        task.setAmount(null);
        List<String> errors = TaskValidator.validate(task);
        assertThat(errors).anyMatch(e -> e.contains("amount"));
    }

    @Test
    void shouldRejectNegativeAmount() {
        TransactionCheckTask task = validTask();
        task.setAmount(new BigDecimal("-1.00"));
        List<String> errors = TaskValidator.validate(task);
        assertThat(errors).anyMatch(e -> e.contains("amount"));
    }

    @Test
    void shouldAcceptZeroAmount() {
        TransactionCheckTask task = validTask();
        task.setAmount(BigDecimal.ZERO);
        List<String> errors = TaskValidator.validate(task);
        assertThat(errors).noneMatch(e -> e.contains("amount"));
    }

    @Test
    void shouldAcceptAmountAtMaxBoundary() {
        TransactionCheckTask task = validTask();
        task.setAmount(new BigDecimal("1000000000000"));
        List<String> errors = TaskValidator.validate(task);
        assertThat(errors).noneMatch(e -> e.contains("amount"));
    }

    @Test
    void shouldRejectAmountExceedingMax() {
        TransactionCheckTask task = validTask();
        task.setAmount(new BigDecimal("1000000000001"));
        List<String> errors = TaskValidator.validate(task);
        assertThat(errors).anyMatch(e -> e.contains("amount"));
    }

    // -- currency --

    @Test
    void shouldRejectNullCurrency() {
        TransactionCheckTask task = validTask();
        task.setCurrency(null);
        List<String> errors = TaskValidator.validate(task);
        assertThat(errors).anyMatch(e -> e.contains("currency"));
    }

    @Test
    void shouldRejectEmptyCurrency() {
        TransactionCheckTask task = validTask();
        task.setCurrency("");
        List<String> errors = TaskValidator.validate(task);
        assertThat(errors).anyMatch(e -> e.contains("currency"));
    }

    @Test
    void shouldRejectTwoCharCurrency() {
        TransactionCheckTask task = validTask();
        task.setCurrency("US");
        List<String> errors = TaskValidator.validate(task);
        assertThat(errors).anyMatch(e -> e.contains("currency"));
    }

    @Test
    void shouldRejectLowercaseCurrency() {
        TransactionCheckTask task = validTask();
        task.setCurrency("usd");
        List<String> errors = TaskValidator.validate(task);
        assertThat(errors).anyMatch(e -> e.contains("currency"));
    }

    @Test
    void shouldRejectInvalidIso4217Currency() {
        TransactionCheckTask task = validTask();
        task.setCurrency("XYZ");
        List<String> errors = TaskValidator.validate(task);
        assertThat(errors).anyMatch(e -> e.contains("currency"));
    }

    @Test
    void shouldAcceptValidCurrencies() {
        for (String code : List.of("USD", "EUR", "GBP", "CNY", "JPY")) {
            TransactionCheckTask task = validTask();
            task.setCurrency(code);
            List<String> errors = TaskValidator.validate(task);
            assertThat(errors).noneMatch(e -> e.contains("currency"));
        }
    }

    // -- timestamp --

    @Test
    void shouldAcceptZeroTimestamp() {
        TransactionCheckTask task = validTask();
        task.setTimestamp(0L);
        List<String> errors = TaskValidator.validate(task);
        assertThat(errors).noneMatch(e -> e.contains("timestamp"));
    }

    @Test
    void shouldRejectNegativeTimestamp() {
        TransactionCheckTask task = validTask();
        task.setTimestamp(-1L);
        List<String> errors = TaskValidator.validate(task);
        assertThat(errors).anyMatch(e -> e.contains("timestamp"));
    }

    @Test
    void shouldAcceptPositiveTimestamp() {
        TransactionCheckTask task = validTask();
        task.setTimestamp(1700000000000L);
        List<String> errors = TaskValidator.validate(task);
        assertThat(errors).noneMatch(e -> e.contains("timestamp"));
    }

    // -- multiple errors --

    @Test
    void shouldReturnMultipleErrorsWhenMultipleFieldsInvalid() {
        TransactionCheckTask task = new TransactionCheckTask();
        task.setRequestId("req-001");
        task.setTransactionId(null);
        task.setPayerAccountId(null);
        task.setPayeeAccountId("payee-99");
        task.setAmount(new BigDecimal("-100.0"));
        task.setCurrency("INVALID");
        task.setTimestamp(-1L);

        List<String> errors = TaskValidator.validate(task);

        assertThat(errors).hasSizeGreaterThanOrEqualTo(4);
        assertThat(errors).anyMatch(e -> e.contains("transactionId"));
        assertThat(errors).anyMatch(e -> e.contains("payerAccountId"));
        assertThat(errors).anyMatch(e -> e.contains("amount"));
        assertThat(errors).anyMatch(e -> e.contains("currency"));
    }
}
