package com.hsbc.fds.rulecheckworker.rule;

import com.hsbc.fds.rulecheckworker.model.DetectionResult;
import com.hsbc.fds.rulecheckworker.model.TransactionCheckTask;

import java.util.Optional;

public interface FraudRule {

    Optional<DetectionResult> check(TransactionCheckTask task);
}
