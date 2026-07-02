package com.hsbc.fds.syncfacade.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsbc.fds.syncfacade.model.TransactionCheckTask;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TicketQueueService {

    private final SqsTemplate sqsTemplate;
    private final ObjectMapper objectMapper;
    private final String ticketQueueUrl;

    public TicketQueueService(SqsTemplate sqsTemplate,
                              ObjectMapper objectMapper,
                              @Value("${fds.sqs.ticket-queue-url}") String ticketQueueUrl) {
        this.sqsTemplate = sqsTemplate;
        this.objectMapper = objectMapper;
        this.ticketQueueUrl = ticketQueueUrl;
    }

    public void sendTask(TransactionCheckTask task) {
        try {
            String payload = objectMapper.writeValueAsString(task);
            sqsTemplate.send(to -> to.queue(ticketQueueUrl).payload(payload));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize task", e);
        }
    }
}
