#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

REGISTRY="${REGISTRY:-}"
TAG="${TAG:-latest}"

echo "=== Building FDS images (tag=$TAG) ==="

REGION="${REGION:-ap-southeast-1}"

for svc in sync-facade rule-check-worker; do
    image="${REGISTRY}/fds/${svc}:${TAG}"
    echo ""
    echo "--- Building $image ---"
    docker build -t "$image" "$REPO_ROOT/services/$svc"
    echo "  Done: $image"
done

echo ""
echo "=== Images built ==="
docker images --filter "reference=*fds-*:$TAG" --format "table {{.Repository}}:{{.Tag}}\t{{.Size}}"

if [ -n "${REGISTRY:-}" ]; then
    echo ""
    echo "=== Logging in to ECR ($REGISTRY) ==="
    aws ecr get-login-password --region "$REGION" | \
        docker login --username AWS --password-stdin "$REGISTRY"

    for svc in sync-facade rule-check-worker; do
        image="${REGISTRY}/fds/${svc}:${TAG}"
        echo "--- Pushing $image ---"
        docker push "$image"
        echo "  Pushed: $image"
    done

    echo ""
    echo "=== All images pushed ==="
fi
