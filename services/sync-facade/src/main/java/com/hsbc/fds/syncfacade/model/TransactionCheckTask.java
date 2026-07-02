package com.hsbc.fds.syncfacade.model;

import java.io.Serializable;

public class TransactionCheckTask implements Serializable {

    private String requestId;
    private String transactionId;
    private String payerAccountId;
    private String payeeAccountId;
    private double amount;
    private String currency;

    public TransactionCheckTask() {
    }

    public TransactionCheckTask(String requestId, String transactionId,
                                String payerAccountId, String payeeAccountId,
                                double amount, String currency) {
        this.requestId = requestId;
        this.transactionId = transactionId;
        this.payerAccountId = payerAccountId;
        this.payeeAccountId = payeeAccountId;
        this.amount = amount;
        this.currency = currency;
    }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getPayerAccountId() { return payerAccountId; }
    public void setPayerAccountId(String payerAccountId) { this.payerAccountId = payerAccountId; }

    public String getPayeeAccountId() { return payeeAccountId; }
    public void setPayeeAccountId(String payeeAccountId) { this.payeeAccountId = payeeAccountId; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
