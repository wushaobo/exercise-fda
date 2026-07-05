package com.hsbc.fds.rulecheckworker.rule;

import com.hsbc.fds.common.model.DetectionResult;
import com.hsbc.fds.common.model.TransactionCheckTask;

import java.util.Optional;

public interface FraudRule {

    Optional<DetectionResult> check(TransactionCheckTask task);
}
