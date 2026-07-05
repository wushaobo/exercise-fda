# FDS Services

Two Spring Boot micro-services that form the Fraud Detection System:

| Service | Role | Port |
|---------|------|------|
| **sync-facade** | gRPC entry point, publishes to SQS, waits for Redis Pub/Sub result | 9090 |
| **rule-check-worker** | SQS consumer, runs fraud rules, publishes result to Redis | — |

## gRPC API

```
service FraudDetectionService {
    rpc CheckTransaction (TransactionCheckRequest) returns (TransactionCheckResponse);
}
```

### Verdict / Reason Business Rules

| Verdict | Reason | Meaning |
|---------|--------|---------|
| `CLEAR` | `NONE` | No fraud detected |
| `CLEAR` | `SYSTEM_ERROR` | System could not process (rate limit, timeout, internal error, validation failure) |
| `SUSPICIOUS` | `AMOUNT_ABOVE_THRESHOLD` | Amount exceeds threshold (default > 10,000) |
| `CONFIRMED_FRAUD` | `PAYEE_IN_DENYLIST` | Payee account is in denylist |

**Key rule**: `SYSTEM_ERROR` always comes with `CLEAR` verdict. The system defaults to "not fraudulent" when it cannot determine fraud status.

### Input Validation

| Layer | Invalid Request Handling                                                                                             |
| ----- | -------------------------------------------------------------------------------------------------------------------- |
| gRPC  | `onError(INVALID_ARGUMENT)` — gRPC status code, no response body                                                     |
| SQS   | Error `DetectionResult` published (`CLEAR` + `SYSTEM_ERROR`), no exception thrown (avoids poison-message retry loop) |

### Validation Rules

| Field | Rule |
|-------|------|
| `transactionId` | Non-null, non-blank |
| `payerAccountId` | Non-null, non-blank |
| `payeeAccountId` | Non-null, non-blank |
| `amount` | Non-null, ≥ 0, ≤ 1,000,000,000,000 |
| `currency` | Non-null, 3-char uppercase valid ISO 4217 code |
| `timestamp` | ≥ 0 (0 = not provided, backward compatible) |
