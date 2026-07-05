package com.hsbc.fds.rulecheckworker.model;

import java.io.Serializable;
import java.math.BigDecimal;

public class TransactionCheckTask implements Serializable {

    private String requestId;
    private String transactionId;
    private String payerAccountId;
    private String payeeAccountId;
    private BigDecimal amount;
    private String currency;
    private long timestamp;

    public TransactionCheckTask() {
    }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getPayerAccountId() { return payerAccountId; }
    public void setPayerAccountId(String payerAccountId) { this.payerAccountId = payerAccountId; }

    public String getPayeeAccountId() { return payeeAccountId; }
    public void setPayeeAccountId(String payeeAccountId) { this.payeeAccountId = payeeAccountId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
