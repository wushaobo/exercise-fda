package com.hsbc.fds.syncfacade.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsbc.fds.proto.FraudReason;
import com.hsbc.fds.proto.FraudVerdict;
import com.hsbc.fds.proto.TransactionCheckResponse;
import com.hsbc.fds.syncfacade.grpc.PendingRequestRegistry;
import com.hsbc.fds.common.model.DetectionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@ConditionalOnProperty(name = "fds.redis.enabled", havingValue = "true", matchIfMissing = true)
public class ResultSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(ResultSubscriber.class);

    private final PendingRequestRegistry registry;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public ResultSubscriber(PendingRequestRegistry registry,
                            RedisTemplate<String, String> redisTemplate,
                            ObjectMapper objectMapper) {
        this.registry = registry;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String requestId = new String(message.getBody());

        log.debug("Received result notification, requestId={}", requestId);

        CompletableFuture<TransactionCheckResponse> future = registry.remove(requestId);
        if (future == null) {
            log.debug("No pending future for requestId={}, may belong to another pod", requestId);
            return;
        }

        try {
            String resultJson = redisTemplate.opsForValue().get(requestId);
            if (resultJson != null) {
                DetectionResult result = objectMapper.readValue(resultJson, DetectionResult.class);
                TransactionCheckResponse response = TransactionCheckResponse.newBuilder()
                        .setTransactionId(result.getTransactionId())
                        .setVerdict(FraudVerdict.valueOf(result.getVerdict()))
                        .setReason(FraudReason.valueOf(result.getReason()))
                        .setMessage(result.getMessage())
                        .build();
                future.complete(response);
            } else {
                future.complete(buildUnknownResponse(requestId));
            }
        } catch (Exception e) {
            log.error("Failed to process result for requestId={}", requestId, e);
            future.complete(buildUnknownResponse(requestId));
        }
    }

    private TransactionCheckResponse buildUnknownResponse(String requestId) {
        return TransactionCheckResponse.newBuilder()
                .setTransactionId("")
                .setVerdict(FraudVerdict.CLEAR)
                .setReason(FraudReason.NONE)
                .setMessage("Result not found for " + requestId)
                .build();
    }
}
