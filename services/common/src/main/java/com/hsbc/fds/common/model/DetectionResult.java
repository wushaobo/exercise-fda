package com.hsbc.fds.common.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
public class DetectionResult implements Serializable {

    private String requestId;
    private String transactionId;
    private String verdict;
    private String reason;
    private String message;

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

    public static DetectionResult systemError(String requestId, String transactionId, String message) {
        DetectionResult r = new DetectionResult();
        r.requestId = requestId;
        r.transactionId = transactionId;
        r.verdict = "CLEAR";
        r.reason = "SYSTEM_ERROR";
        r.message = message;
        return r;
    }
}
