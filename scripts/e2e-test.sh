#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "=== FDS E2E Test Suite ==="

cleanup() {
  if [ $? -ne 0 ]; then
    echo "--- Service logs (failure) ---"
    cd "$REPO_ROOT/docker" && docker compose logs --tail=50 2>/dev/null || true
  fi
  echo "Tearing down..."
  cd "$REPO_ROOT/docker" && docker compose down 2>/dev/null || true
}
trap cleanup EXIT

echo ""
echo "--- Building service JARs ---"
for svc in sync-facade rule-check-worker; do
    echo "  Building $svc..."
    cd "$REPO_ROOT/services/$svc"
    ./gradlew bootJar -x test -q
done

echo ""
echo "--- Building e2e client ---"
cd "$REPO_ROOT/e2e-test"
./gradlew build -q

echo ""
echo "--- Starting services ---"
cd "$REPO_ROOT/docker"
docker compose up -d --build

echo "--- Waiting for services ---"
sleep 20

echo ""
echo "--- Seeding denylist ---"
docker compose exec -T redis redis-cli SET fds:denylist "account-blocked-1" >/dev/null
sleep 6

echo ""
echo "--- Running e2e tests ---"
cd "$REPO_ROOT/e2e-test"
./gradlew e2eClient -q 2>&1

echo ""
echo "=== E2E Complete ==="
