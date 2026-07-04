#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

CLUSTER_NAME="${CLUSTER_NAME:-fds-uat}"
REGION="${REGION:-ap-southeast-1}"

echo "=== Updating kubeconfig for $CLUSTER_NAME ($REGION) ==="
aws eks update-kubeconfig --name "$CLUSTER_NAME" --region "$REGION"

echo ""
echo "=== Adding ArgoCD Helm repo ==="
helm repo add argo https://argoproj.github.io/argo-helm || true
helm repo update

echo ""
echo "=== Installing ArgoCD ==="
helm upgrade --install argo-cd argo/argo-cd \
    --namespace argocd \
    --create-namespace \
    --values "$REPO_ROOT/deploy/argocd/argocd-values.yaml" \
    --wait \
    --timeout 5m

echo ""
echo "=== Waiting for ArgoCD pods Ready ==="
kubectl wait --for=condition=Ready pods --all -n argocd --timeout=300s

echo ""
echo "=== Applying FDS Application CRD ==="
kubectl apply -f "$REPO_ROOT/deploy/argocd/fds-app.yaml"

echo ""
echo "=== ArgoCD initial admin password ==="
PASS=$(kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" 2>/dev/null | base64 -d || echo "(secret not found — may have been changed)")
echo "$PASS"

echo ""
echo "=== Done ==="
echo "Access ArgoCD UI:"
echo "  kubectl port-forward -n argocd svc/argo-cd-argocd-server 8080:443"
echo "  https://localhost:8080  (user: admin, password: above)"
echo ""
echo "Check FDS sync status:"
echo "  kubectl -n argocd get application fds"
