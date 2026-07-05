package com.hsbc.fds.rulecheckworker.rule;

import com.hsbc.fds.common.model.DetectionResult;
import com.hsbc.fds.common.model.TransactionCheckTask;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

@Component
public class DenylistRule implements FraudRule {

    private volatile Set<String> denylist = Set.of();

    @Override
    public Optional<DetectionResult> check(TransactionCheckTask task) {
        String payee = task.getPayeeAccountId();
        if (payee != null && denylist.contains(payee.toLowerCase())) {
            return Optional.of(DetectionResult.confirmedFraud(
                    task.getRequestId(),
                    task.getTransactionId(),
                    "PAYEE_IN_DENYLIST",
                    "Payee account " + task.getPayeeAccountId() + " is in denylist"));
        }
        return Optional.empty();
    }

    public void updateDenylist(Set<String> accounts) {
        this.denylist = Set.copyOf(accounts);
    }

    public Set<String> getDenylist() {
        return denylist;
    }
}
