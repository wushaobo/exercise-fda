package com.hsbc.fds.rulecheckworker.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsbc.fds.common.model.DetectionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

@ExtendWith(MockitoExtension.class)
class ResultPublisherTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Captor
    private ArgumentCaptor<String> jsonCaptor;

    @Captor
    private ArgumentCaptor<String> channelCaptor;

    @Captor
    private ArgumentCaptor<String> messageCaptor;

    @Test
    void shouldStoreResultInRedisWithTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ResultPublisher publisher = new ResultPublisher(redisTemplate, objectMapper, "fds:results", 300);

        DetectionResult result = DetectionResult.suspicious("req-1", "tx-001", "AMOUNT_ABOVE_THRESHOLD", "exceeded");
        publisher.publish(result);

        verify(valueOperations).set(eq("req-1"), jsonCaptor.capture(), eq(Duration.ofSeconds(300)));
        assertThat(jsonCaptor.getValue()).contains("AMOUNT_ABOVE_THRESHOLD");
    }

    @Test
    void shouldPublishNotificationToChannel() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ResultPublisher publisher = new ResultPublisher(redisTemplate, objectMapper, "fds:results", 300);

        DetectionResult result = DetectionResult.clear("req-2", "tx-002");
        publisher.publish(result);

        verify(redisTemplate).convertAndSend(channelCaptor.capture(), messageCaptor.capture());
        assertThat(channelCaptor.getValue()).isEqualTo("fds:results");
        assertThat(messageCaptor.getValue()).isEqualTo("req-2");
    }
}
