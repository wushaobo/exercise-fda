# FDS — Fraud Detection System

Real-time fraud detection microservice built with Java 21 virtual threads, deployed on AWS EKS.

## Architecture

```
gRPC Client → Sync Facade → SQS Ticket Queue → Rule Check Worker
                   ↑                                      ↓
                   │                              Redis SET + PUBLISH
                   │                                      │
                   └──────── Redis Pub/Sub ───────────────┘
```

| Component | Role |
|-----------|------|
| **Sync Facade** | gRPC server, receives check requests, dispatches to SQS, waits via virtual threads |
| **Rule Check Worker** | SQS consumer, runs rule engine, publishes result to Redis |
| **SQS Ticket Queue** | Async task buffer between Facade and Worker |
| **Redis** | Pub/Sub result notification, result caching (TTL), denylist storage |

### Detection Rules

- **Amount Threshold**: amount > $10,000 → SUSPICIOUS
- **Denylist Match**: payee account in denylist → CONFIRMED_FRAUD

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 (Temurin) with Virtual Threads |
| Build | Gradle 9.6.1 |
| Framework | Spring Boot 3.5.16 |
| gRPC | grpc-java 1.76.3 |
| Message Queue | AWS SQS (LocalStack for local dev) |
| Cache/PubSub | Redis 7 (ElastiCache for production) |
| Logging | Fluent Bit → CloudWatch |
| Infra | Terraform (AWS Singapore, ap-southeast-1) |
| CI/CD | GitHub Actions + ArgoCD |
| Deployment | Kubernetes (EKS), Helm, K8s YAML, k3d for local |

## Quick Start

### Prerequisites

- Java 21 (Temurin), Gradle 9.6.1 (via SDKMAN)
- Docker

### Build & Test

```bash
# Run all tests with coverage
bash scripts/test-all.sh

# Or per service
cd services/sync-facade && gradle test jacocoTestReport
cd services/rule-check-worker && gradle test jacocoTestReport
```

### Local Development

```bash
bash scripts/dev-up.sh
```

This starts LocalStack (SQS), Redis, Sync Facade, and Rule Check Worker via Docker Compose.

- Sync Facade gRPC: `localhost:9090`
- Sync Facade HTTP: `localhost:8080`
- Worker HTTP: `localhost:8081`
- LocalStack SQS: `localhost:4566`
- Redis: `localhost:6379`

Stop: `cd docker && docker compose down`

## Deployment

### Kubernetes

```bash
# Raw manifests
kubectl apply -f deploy/k8s/

# Or via Helm
helm install fds deploy/helm/fds-chart \
  --set config.ticketQueueUrl=<sqs-url> \
  --set config.redisHost=<redis-host>
```

### AWS Infrastructure

```bash
cd infra
terraform init
terraform apply
```

## Project Structure

```
fds/
├── services/
│   ├── sync-facade/          # gRPC server
│   └── rule-check-worker/    # Detection worker
├── docker/                   # docker-compose.yml
├── deploy/
│   ├── k8s/                  # Raw K8s manifests
│   └── helm/fds-chart/       # Helm chart
├── infra/                    # Terraform (AWS)
├── scripts/                  # Utility scripts
└── .github/workflows/        # CI
```
