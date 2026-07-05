package com.hsbc.fds.rulecheckworker.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsbc.fds.rulecheckworker.model.DetectionResult;
import com.hsbc.fds.rulecheckworker.model.TransactionCheckTask;
import com.hsbc.fds.rulecheckworker.redis.ResultPublisher;
import com.hsbc.fds.rulecheckworker.rule.RuleEngine;
import com.hsbc.fds.rulecheckworker.validation.TaskValidator;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TicketQueueListener {

    private static final Logger log = LoggerFactory.getLogger(TicketQueueListener.class);

    private final RuleEngine ruleEngine;
    private final ResultPublisher resultPublisher;
    private final ObjectMapper objectMapper;

    public TicketQueueListener(RuleEngine ruleEngine, ResultPublisher resultPublisher, ObjectMapper objectMapper) {
        this.ruleEngine = ruleEngine;
        this.resultPublisher = resultPublisher;
        this.objectMapper = objectMapper;
    }

    @SqsListener("${fds.sqs.ticket-queue-url}")
    public void onMessage(String message) {
        try {
            TransactionCheckTask task = objectMapper.readValue(message, TransactionCheckTask.class);

            // Validate deserialized task; publish error result for invalid messages
            // instead of throwing, to avoid poison-message infinite retry loops
            List<String> errors = TaskValidator.validate(task);
            if (!errors.isEmpty()) {
                log.warn("Validation failed for task, requestId={}, transactionId={}, errors={}",
                        task.getRequestId(), task.getTransactionId(), errors);
                String reqId = task.getRequestId() != null ? task.getRequestId() : "unknown";
                String txId = task.getTransactionId() != null ? task.getTransactionId() : "unknown";
                DetectionResult errorResult = DetectionResult.systemError(
                        reqId, txId,
                        "Validation failed: " + String.join("; ", errors));
                resultPublisher.publish(errorResult);
                return;
            }

            log.info("Processing task: requestId={}, transactionId={}, amount={}",
                    task.getRequestId(), task.getTransactionId(), task.getAmount());

            DetectionResult result = ruleEngine.execute(task);

            log.info("Detection complete: requestId={}, verdict={}, reason={}",
                    result.getRequestId(), result.getVerdict(), result.getReason());

            resultPublisher.publish(result);

        } catch (Exception e) {
            log.error("Failed to process SQS message", e);
            throw new RuntimeException("Failed to process SQS message", e);
        }
    }
}
