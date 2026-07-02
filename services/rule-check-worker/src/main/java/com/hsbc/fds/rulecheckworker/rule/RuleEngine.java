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
        for (FraudRule rule : rules) {
            var result = rule.check(task);
            if (result.isPresent()) {
                log.info("Rule hit: requestId={}, verdict={}, reason={}",
                        task.getRequestId(), result.get().getVerdict(), result.get().getReason());
                return result.get();
            }
        }

        return DetectionResult.clear(task.getRequestId(), task.getTransactionId());
    }
}
