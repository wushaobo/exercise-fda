package com.hsbc.fds.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionCheckTask implements Serializable {

    private String requestId;
    private String transactionId;
    private String payerAccountId;
    private String payeeAccountId;
    private BigDecimal amount;
    private String currency;
    private long timestamp;
}
