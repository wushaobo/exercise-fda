package com.hsbc.fds.syncfacade.model;

import java.io.Serializable;

public class DetectionResult implements Serializable {

    private String requestId;
    private String transactionId;
    private String verdict;
    private String reason;
    private String message;

    public DetectionResult() {
    }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
