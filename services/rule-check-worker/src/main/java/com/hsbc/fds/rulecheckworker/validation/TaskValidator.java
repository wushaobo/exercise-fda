package com.hsbc.fds.rulecheckworker.validation;

import com.hsbc.fds.rulecheckworker.model.TransactionCheckTask;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

public final class TaskValidator {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000000");

    private TaskValidator() {
    }

    /**
     * Validates a TransactionCheckTask.
     *
     * @return list of validation error messages; empty list means valid
     */
    public static List<String> validate(TransactionCheckTask task) {
        List<String> errors = new ArrayList<>();

        if (isBlank(task.getTransactionId())) {
            errors.add("transactionId must not be blank");
        }
        if (isBlank(task.getPayerAccountId())) {
            errors.add("payerAccountId must not be blank");
        }
        if (isBlank(task.getPayeeAccountId())) {
            errors.add("payeeAccountId must not be blank");
        }

        // amount
        if (task.getAmount() == null) {
            errors.add("amount must not be null");
        } else {
            BigDecimal amount = task.getAmount();
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                errors.add("amount must be non-negative");
            }
            if (amount.compareTo(MAX_AMOUNT) > 0) {
                errors.add("amount must not exceed " + MAX_AMOUNT);
            }
        }

        // currency
        if (isBlank(task.getCurrency())) {
            errors.add("currency must not be blank");
        } else {
            String currency = task.getCurrency().trim();
            if (currency.length() != 3) {
                errors.add("currency must be a 3-letter ISO 4217 code");
            } else if (!currency.equals(currency.toUpperCase())) {
                errors.add("currency must be uppercase");
            } else {
                try {
                    Currency.getInstance(currency);
                } catch (IllegalArgumentException e) {
                    errors.add("currency must be a valid ISO 4217 code");
                }
            }
        }

        // timestamp
        if (task.getTimestamp() < 0) {
            errors.add("timestamp must not be negative");
        }

        return errors;
    }

    /**
     * Convenience: returns true if the task passes all validations.
     */
    public static boolean isValid(TransactionCheckTask task) {
        return validate(task).isEmpty();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
