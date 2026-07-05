package com.hsbc.fds.rulecheckworker.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsbc.fds.rulecheckworker.model.DetectionResult;
import com.hsbc.fds.rulecheckworker.model.TransactionCheckTask;
import com.hsbc.fds.rulecheckworker.redis.ResultPublisher;
import com.hsbc.fds.rulecheckworker.rule.RuleEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TicketQueueListenerTest {

    @Mock
    private RuleEngine ruleEngine;

    @Mock
    private ResultPublisher resultPublisher;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private TicketQueueListener listener;

    @Captor
    private ArgumentCaptor<DetectionResult> resultCaptor;

    private static final String VALID_JSON = """
            {
                "requestId": "req-001",
                "transactionId": "tx-001",
                "payerAccountId": "payer-1",
                "payeeAccountId": "payee-99",
                "amount": 50000.0,
                "currency": "USD",
                "timestamp": 1700000000000
            }
            """;

    @Test
    void shouldPropagateExceptionWhenRuleEngineFails() {
        when(ruleEngine.execute(any(TransactionCheckTask.class)))
                .thenThrow(new RuntimeException("Rule engine failure"));

        assertThatThrownBy(() -> listener.onMessage(VALID_JSON))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to process SQS message");

        verify(ruleEngine).execute(any(TransactionCheckTask.class));
    }

    @Test
    void shouldPropagateExceptionWhenRedisPublishFails() {
        when(ruleEngine.execute(any(TransactionCheckTask.class)))
                .thenReturn(DetectionResult.clear("req-001", "tx-001"));
        doThrow(new RuntimeException("Redis connection lost"))
                .when(resultPublisher).publish(any(DetectionResult.class));

        assertThatThrownBy(() -> listener.onMessage(VALID_JSON))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to process SQS message");

        verify(ruleEngine).execute(any(TransactionCheckTask.class));
        verify(resultPublisher).publish(any(DetectionResult.class));
    }

    @Test
    void shouldProcessValidMessageSuccessfully() {
        when(ruleEngine.execute(any(TransactionCheckTask.class)))
                .thenReturn(DetectionResult.clear("req-001", "tx-001"));

        assertThatCode(() -> listener.onMessage(VALID_JSON))
                .doesNotThrowAnyException();

        verify(ruleEngine).execute(any(TransactionCheckTask.class));
        verify(resultPublisher).publish(any(DetectionResult.class));
    }

    // -- validation rejection tests --

    @Test
    void shouldPublishErrorResultWhenTransactionIdBlank() {
        String json = """
                {
                    "requestId": "req-001",
                    "transactionId": "",
                    "payerAccountId": "payer-1",
                    "payeeAccountId": "payee-99",
                    "amount": 50000.0,
                    "currency": "USD",
                    "timestamp": 1700000000000
                }
                """;

        assertThatCode(() -> listener.onMessage(json))
                .doesNotThrowAnyException();

        verify(ruleEngine, never()).execute(any(TransactionCheckTask.class));
        verify(resultPublisher).publish(resultCaptor.capture());
        DetectionResult result = resultCaptor.getValue();
        assertThat(result.getVerdict()).isEqualTo("SUSPICIOUS");
        assertThat(result.getReason()).isEqualTo("SYSTEM_ERROR");
        assertThat(result.getMessage()).contains("transactionId");
    }

    @Test
    void shouldPublishErrorResultWhenAmountNegative() {
        String json = """
                {
                    "requestId": "req-001",
                    "transactionId": "tx-001",
                    "payerAccountId": "payer-1",
                    "payeeAccountId": "payee-99",
                    "amount": -100.0,
                    "currency": "USD",
                    "timestamp": 1700000000000
                }
                """;

        assertThatCode(() -> listener.onMessage(json))
                .doesNotThrowAnyException();

        verify(ruleEngine, never()).execute(any(TransactionCheckTask.class));
        verify(resultPublisher).publish(resultCaptor.capture());
        DetectionResult result = resultCaptor.getValue();
        assertThat(result.getVerdict()).isEqualTo("SUSPICIOUS");
        assertThat(result.getReason()).isEqualTo("SYSTEM_ERROR");
        assertThat(result.getMessage()).contains("amount");
    }

    @Test
    void shouldPublishErrorResultWhenCurrencyInvalid() {
        String json = """
                {
                    "requestId": "req-001",
                    "transactionId": "tx-001",
                    "payerAccountId": "payer-1",
                    "payeeAccountId": "payee-99",
                    "amount": 50000.0,
                    "currency": "INVALID",
                    "timestamp": 1700000000000
                }
                """;

        assertThatCode(() -> listener.onMessage(json))
                .doesNotThrowAnyException();

        verify(ruleEngine, never()).execute(any(TransactionCheckTask.class));
        verify(resultPublisher).publish(resultCaptor.capture());
        DetectionResult result = resultCaptor.getValue();
        assertThat(result.getVerdict()).isEqualTo("SUSPICIOUS");
        assertThat(result.getReason()).isEqualTo("SYSTEM_ERROR");
        assertThat(result.getMessage()).contains("currency");
    }

    @Test
    void shouldPublishErrorResultWhenAmountMissing() {
        String json = """
                {
                    "requestId": "req-001",
                    "transactionId": "tx-001",
                    "payerAccountId": "payer-1",
                    "payeeAccountId": "payee-99",
                    "currency": "USD",
                    "timestamp": 1700000000000
                }
                """;

        assertThatCode(() -> listener.onMessage(json))
                .doesNotThrowAnyException();

        verify(ruleEngine, never()).execute(any(TransactionCheckTask.class));
        verify(resultPublisher).publish(resultCaptor.capture());
        DetectionResult result = resultCaptor.getValue();
        assertThat(result.getVerdict()).isEqualTo("SUSPICIOUS");
        assertThat(result.getReason()).isEqualTo("SYSTEM_ERROR");
        assertThat(result.getMessage()).contains("amount");
    }
}
