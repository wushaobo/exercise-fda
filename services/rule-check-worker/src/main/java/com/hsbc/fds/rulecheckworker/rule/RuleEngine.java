package com.hsbc.fds.rulecheckworker.rule;

import com.hsbc.fds.rulecheckworker.model.DetectionResult;
import com.hsbc.fds.rulecheckworker.model.TransactionCheckTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RuleEngine {

    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);

    private final List<FraudRule> rules;

    public RuleEngine(List<FraudRule> rules) {
        this.rules = rules;
    }

    public DetectionResult execute(TransactionCheckTask task) {
        DetectionResult worst = null;
        for (FraudRule rule : rules) {
            var result = rule.check(task);
            if (result.isPresent()) {
                DetectionResult current = result.get();
                log.info("Rule hit: requestId={}, verdict={}, reason={}",
                        task.getRequestId(), current.getVerdict(), current.getReason());
                if (worst == null || severity(current) > severity(worst)) {
                    worst = current;
                }
            }
        }
        if (worst != null) {
            return worst;
        }

        return DetectionResult.clear(task.getRequestId(), task.getTransactionId());
    }

    /**
     * Returns a numeric severity score for the verdict.
     * Higher = more severe. Used to ensure the most severe match wins
     * regardless of rule evaluation order.
     */
    private static int severity(DetectionResult r) {
        return switch (r.getVerdict()) {
            case "CONFIRMED_FRAUD" -> 3;
            case "SUSPICIOUS" -> 2;
            default -> 1;
        };
    }
}
