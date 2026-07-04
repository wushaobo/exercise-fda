#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CLUSTER="fds-dev"
NAMESPACE="fds"

echo "=== FDS k3d Deployment ==="

echo ""
echo "--- Step 1: Create k3d cluster ---"
if k3d cluster list 2>/dev/null | grep -q "$CLUSTER"; then
    echo "Cluster $CLUSTER already exists"
else
    k3d cluster create "$CLUSTER" --agents 1 \
        -p "9090:30090@server:0" \
        --k3s-arg "--disable=traefik@server:*"
fi

echo ""
echo "--- Step 2: Preload k3d system images ---"
docker pull rancher/mirrored-pause:3.6 -q 2>/dev/null || true
docker pull rancher/mirrored-coredns-coredns:1.14.3 -q 2>/dev/null || true
docker pull cr.fluentbit.io/fluent/fluent-bit:5.0.8 || true
k3d image import rancher/mirrored-pause:3.6 rancher/mirrored-coredns-coredns:1.14.3 cr.fluentbit.io/fluent/fluent-bit:5.0.8 -c "$CLUSTER"
kubectl -n kube-system wait --for=condition=available deployment/coredns --timeout=60s 2>/dev/null || true

echo ""
echo "--- Step 3: Build images ---"
cd "$REPO_ROOT"
bash scripts/build-images.sh

echo ""
echo "--- Step 4: Import images to k3d ---"
k3d image import fds/sync-facade:latest fds/rule-check-worker:latest -c "$CLUSTER"

echo ""
echo "--- Step 5: Start infrastructure ---"
cd "$REPO_ROOT/docker"
docker compose up -d localstack redis
sleep 5

echo ""
echo "--- Step 6: Install Helm chart ---"
cd "$REPO_ROOT"
helm upgrade --install fds deploy/helm/fds-chart \
    -f deploy/helm/fds-chart/values-k3d.yaml \
    -n "$NAMESPACE" --create-namespace

echo ""
echo "--- Step 7: Install Fluent Bit ---"
helm repo add fluent https://fluent.github.io/helm-charts 2>/dev/null || true
helm upgrade --install fluent-bit fluent/fluent-bit \
    -f deploy/monitoring/fluent-bit-values-k3d.yaml \
    -n "$NAMESPACE" --create-namespace
sleep 5
kubectl -n "$NAMESPACE" wait --for=condition=available daemonset/fluent-bit --timeout=120s

echo ""
echo "--- Step 8: Wait for pods ---"
kubectl -n "$NAMESPACE" wait --for=condition=available deployment/sync-facade --timeout=120s
kubectl -n "$NAMESPACE" wait --for=condition=available deployment/rule-check-worker --timeout=120s

echo ""
echo "--- Step 9: Seed denylist ---"
docker exec docker-redis-1 redis-cli SET fds:denylist "account-blocked-1" >/dev/null
sleep 6

echo ""
echo "--- Step 10: Run e2e tests ---"
# Port 19090 avoids the k3d -p 9090:30090 host mapping.
kubectl -n "$NAMESPACE" port-forward svc/sync-facade 19090:9090 &
PF_PID=$!
sleep 3
cd "$REPO_ROOT/e2e-test"
./gradlew -Dgrpc.port=19090 e2eClient -q
kill $PF_PID 2>/dev/null || true

echo ""
echo "--- Step 11: Verify OTel metrics ---"
bash "$REPO_ROOT/scripts/verify-otel-metrics.sh"

echo ""
echo "=== FDS k3d Deployment Complete ==="
