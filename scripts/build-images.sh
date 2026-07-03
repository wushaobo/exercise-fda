#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

REGISTRY="${REGISTRY:-}"
TAG="${TAG:-latest}"
SECRET_FLAG=""

if [ -f "$HOME/.gradle/init.d/init.gradle" ]; then
    SECRET_FLAG="--secret id=gradle-init,src=$HOME/.gradle/init.d/init.gradle"
fi

echo "=== Building FDS images (tag=$TAG) ==="

for svc in sync-facade rule-check-worker; do
    image="${REGISTRY}fds-${svc}:${TAG}"
    echo ""
    echo "--- Building $image ---"
    docker build $SECRET_FLAG -t "$image" "$REPO_ROOT/services/$svc"
    echo "  Done: $image"
done

echo ""
echo "=== Images built ==="
docker images --filter "reference=*fds-*:$TAG" --format "table {{.Repository}}:{{.Tag}}\t{{.Size}}"
