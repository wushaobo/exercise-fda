# FDS — Fraud Detection Service

Real-time fraud detection micro-service built with Java, deployed on AWS.

## Architecture

```mermaid
flowchart TB
    Upstream[Upstream Service] -->|gRPC| Facade[Sync Facade]
    Facade --> Upstream
    Facade --> Queue[AWS SQS]
    Facade -->|Observability| CloudWatch[AWS CloudWatch]
    Queue --> Worker[Rule Check Worker]
    Worker -->|GET denylist| Cache[(ElastiCache)]
    Worker -->|SET + PUBLISH| Cache
    Worker -->|alert| SNS[AWS SNS]
    Worker -->|Observability| CloudWatch
    Cache -->|SUBSCRIBE + GET| Facade
```

### Tech Stack

| Layer           | Technology                         |
| --------------- | ---------------------------------- |
| Language        | Java 21.0.11 (Temurin)             |
| Framework       | Spring Boot 3.5.16                 |
| gRPC            | grpc-java 1.76.3                   |
| Message Queue   | AWS SQS (LocalStack for dev/CI)    |
| Cache + Pub/Sub | AWS ElastiCache (Redis for dev/CI) |
| Observability   | AWS CloudWatch                     |
| Infrastructure  | Terraform                          |
| CI/CD           | GitHub Actions + ArgoCD            |
| Deployment      | Kubernetes (EKS), Helm             |


### Scripts Explanation

```
./scripts/test-all.sh              # unit/integration tests + coverage
./scripts/e2e-test.sh              # e2e tests, based on docker compose
./scripts/build-images.sh          # build docker images, push to registry
./scripts/deploy-k3d.sh            # full local deploy
./scripts/bootstrap-argocd.sh      # install ArgoCD on EKS
```

## Deployment

### AWS Infrastructure
- Terraform (see [`infra/README.md`](infra/README.md))
- VPC, EKS, SQS, ElastiCache, SNS, CloudWatch, ECR


### K8s Cluster + Observability

| Layer      | Tools                                    |
| ---------- | ---------------------------------------- |
| Cluster    | k3d (local), EKS (prod)                  |
| Deployment | Helm                                     |
| GitOps     | ArgoCD                                   |
| Logging    | Fluent Bit → CloudWatch                  |
| Metrics    | OTel Collector → CloudWatch EMF         |
| Tracing    | OTel Collector → AWS X-Ray              |
| Dashboards | CloudWatch                               |

## Testing
### Uint/Integration Test Coverage
- `sync-facade`: Lines: 136/186, Instructions: 75%, Branches: 80%
- `rule-check-worker`: Lines: 125/138, Instructions: 91%, Branches: 100%

#### Business Rules in Response

See [`services/README.md`](services/README.md) for the complete verdict/reason mapping, input validation rules, and data flow.

| Verdict | Reason | Scenario |
|---------|--------|----------|
| `CLEAR` | `NONE` | No fraud detected |
| `CLEAR` | `SYSTEM_ERROR` | System fault (rate limit, timeout, validation failure) |
| `SUSPICIOUS` | `AMOUNT_ABOVE_THRESHOLD` | Amount > 10,000 |
| `CONFIRMED_FRAUD` | `PAYEE_IN_DENYLIST` | Payee in denylist |

### E2E Test Cases
- Normal transaction → CLEAR
- High amount (>10,000) → SUSPICIOUS
- Denylist payee → CONFIRMED_FRAUD

### Load Test

#### Test Environment
**System Under Test** — EKS `ap-southeast-1`:
fds-pool **t3.medium × 2** (ASG min=1, max=4), monitor-pool t3.medium × 1.
sync-facade: 250m/1 CPU, 512Mi/1Gi, HPA 2→4 @ CPU 40%.
rule-check-worker: 250m/1 CPU, 512Mi/1Gi, HPA 2→10 @ CPU 70%.

**Load Generator** — EC2 **t3.medium** (2 vCPU, 4 GiB) in same VPC, **ghz** c=100, 3m, connections=4, timeout=10s against internal NLB:9090. Payload: 90/9/1 mix (CLEAR / SUSPICIOUS / CONFIRMED_FRAUD).

**Seed Data** - Denylist in ElastiCache

#### Test Results Summary
| Metric  | Value    |
| ------- | -------- |
| Count   | 85032    |
| Total   | 180.00s  |
| Fastest | 5.17ms   |
| P10     | 103.83ms |
| P95     | 778.34ms |
| P99     | 1.14s    |
| Slowest | 8.66s    |
| RPS     | 472.40   |

### Resilience Test
