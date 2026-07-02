#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "=== FDS Local Dev Environment ==="

echo ""
echo "--- Building services ---"
for svc in sync-facade rule-check-worker; do
    echo "Building $svc..."
    cd "$REPO_ROOT/services/$svc"
    gradle build -x test
done

echo ""
echo "--- Starting docker-compose ---"
cd "$REPO_ROOT/docker"
docker compose up --build -d

echo ""
echo "=== Services started ==="
echo "sync-facade:       localhost:8080 (HTTP), localhost:9090 (gRPC)"
echo "rule-check-worker: localhost:8081 (HTTP)"
echo "LocalStack SQS:    localhost:4566"
echo "Redis:             localhost:6379"
echo ""
echo "To stop: cd docker && docker compose down"
