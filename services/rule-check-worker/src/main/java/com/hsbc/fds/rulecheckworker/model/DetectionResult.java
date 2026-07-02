package com.hsbc.fds.rulecheckworker.model;

import java.io.Serializable;

public class DetectionResult implements Serializable {

    private String requestId;
    private String transactionId;
    private String verdict;
    private String reason;
    private String message;

    public DetectionResult() {
    }

    public static DetectionResult clear(String requestId, String transactionId) {
        DetectionResult r = new DetectionResult();
        r.requestId = requestId;
        r.transactionId = transactionId;
        r.verdict = "CLEAR";
        r.reason = "NONE";
        r.message = "No fraud detected";
        return r;
    }

    public static DetectionResult suspicious(String requestId, String transactionId, String reason, String message) {
        DetectionResult r = new DetectionResult();
        r.requestId = requestId;
        r.transactionId = transactionId;
        r.verdict = "SUSPICIOUS";
        r.reason = reason;
        r.message = message;
        return r;
    }

    public static DetectionResult confirmedFraud(String requestId, String transactionId, String reason, String message) {
        DetectionResult r = new DetectionResult();
        r.requestId = requestId;
        r.transactionId = transactionId;
        r.verdict = "CONFIRMED_FRAUD";
        r.reason = reason;
        r.message = message;
        return r;
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
