#!/usr/bin/env bash
#
# FDS perf test runner — local one-shot entry.
#
# Pre-generates a 90/9/1 mixed request array, seeds the Redis denylist, exposes
# sync-facade via an internal NLB, runs ghz on an isolated same-VPC EC2 against
# the NLB, copies the HTML report back, then cleans up. User runs only this.
#
# Usage:
#   ./perf-test/run.sh                       # default: c=100, 3m
#   CONCURRENCY=1 DURATION=5s ./perf-test/run.sh   # smoke test
#   N=5000 CONCURRENCY=200 ./perf-test/run.sh
#
# Prereq:
#   - terraform apply (ghz_runner EC2 + CloudWatch dashboard).
#   - ghz binary installed on the EC2 (manually). If not on $PATH, run with
#     GHZ_BIN=<path-to-ghz> ./perf-test/run.sh
#   See .claude/perf-test-plan.md.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
INFRA_DIR="$REPO_ROOT/infra"
NS="${NAMESPACE:-fds}"
CONCURRENCY="${CONCURRENCY:-100}"
DURATION="${DURATION:-3m}"
N="${N:-1000}"
# ghz binary path on the EC2 (user installs manually). Override if not on PATH:
#   GHZ_BIN=~/ghz ./perf-test/run.sh
GHZ_BIN="${GHZ_BIN:-ghz}"
RESULTS_DIR="$SCRIPT_DIR/results"
TS="$(date +%Y%m%d-%H%M%S)"
HPA_LOG="$RESULTS_DIR/hpa-$TS.log"
KEY_FILE="/tmp/ghz-key-$$"
REMOTE_DIR="/home/ec2-user"

mkdir -p "$RESULTS_DIR"

echo "=== FDS perf test ==="
echo "  mix:           90/9/1 (clear/suspicious/fraud), N=$N"
echo "  concurrency:   $CONCURRENCY"
echo "  duration:      $DURATION"
echo "  results:       $RESULTS_DIR"
echo ""

# --- 0. fetch EC2 IP + ssh key from terraform ---
echo "--- Fetching ghz runner EC2 IP + key ---"
EC2_IP="$(terraform -chdir="$INFRA_DIR" output -raw ghz_runner_public_ip)"
terraform -chdir="$INFRA_DIR" output -raw ghz_runner_private_key > "$KEY_FILE"
chmod 600 "$KEY_FILE"
SSH="ssh -i $KEY_FILE -o StrictHostKeyChecking=no -o ConnectTimeout=10 ec2-user@$EC2_IP"
SCP="scp -i $KEY_FILE -o StrictHostKeyChecking=no"
echo "  EC2: $EC2_IP"

cleanup() {
  echo ""
  echo "--- Cleanup ---"
  kill "$HPA_PID" 2>/dev/null || true
  kubectl -n "$NS" delete -f "$SCRIPT_DIR/nlb-service.yaml" --ignore-not-found >/dev/null 2>&1 || true
  kubectl -n "$NS" delete pod redis-seed --ignore-not-found >/dev/null 2>&1 || true
  rm -f "$KEY_FILE"
}
trap cleanup EXIT

# --- 1. pre-generate payload ---
echo "--- Generating 90/9/1 payload array ($N) ---"
python3 "$SCRIPT_DIR/gen-payload.py" "$N" > "$RESULTS_DIR/payload.json"

# --- 2. seed denylist (fraud path) ---
echo "--- Seeding Redis denylist (account-blocked-1) ---"
REDIS_HOST="$(terraform -chdir="$INFRA_DIR" output -raw redis_endpoint)"
REDIS_PASS="$(kubectl -n "$NS" get secret fds-secret -o jsonpath='{.data.redis-password}' 2>/dev/null | base64 -d 2>/dev/null || true)"
if [ -z "$REDIS_PASS" ]; then
  echo "  WARN: could not read redis password from fds-secret; fraud path may not trigger"
fi
kubectl -n "$NS" run redis-seed --rm -i --restart=Never --image=redis:alpine -- \
  redis-cli --tls --user default -a "$REDIS_PASS" -h "$REDIS_HOST" SET fds:denylist account-blocked-1 >/dev/null 2>&1 || \
  echo "  WARN: denylist seed failed (continuing)"
echo "  waiting 65s for rule-check-worker DenylistCache refresh..."
sleep 65

# --- 3. NLB ---
echo "--- Applying sync-facade internal NLB ---"
kubectl -n "$NS" apply -f "$SCRIPT_DIR/nlb-service.yaml"
echo "  waiting for NLB DNS..."
NLB_DNS=""
for i in $(seq 1 40); do
  NLB_DNS="$(kubectl -n "$NS" get svc sync-facade-nlb -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || true)"
  [ -n "$NLB_DNS" ] && break
  sleep 5
done
[ -z "$NLB_DNS" ] && { echo "  NLB DNS not ready, aborting"; exit 1; }
echo "  NLB: $NLB_DNS:9090"

# --- 4. HPA watch (background, own PID) ---
echo "--- HPA watch → $HPA_LOG ---"
kubectl -n "$NS" get hpa sync-facade -w > "$HPA_LOG" 2>&1 &
HPA_PID=$!

# --- 5. wait for ghz binary on EC2 ---
echo "--- Waiting for ghz on EC2 ($GHZ_BIN) ---"
for i in $(seq 1 30); do
  if $SSH "$GHZ_BIN --version" >/dev/null 2>&1; then echo "  ghz ready"; break; fi
  sleep 5
done
$SSH "$GHZ_BIN --version" >/dev/null 2>&1 || { echo "  ghz not found at '$GHZ_BIN' on EC2; set GHZ_BIN=<path> and rerun"; exit 1; }

# --- 6. upload payload + proto ---
echo "--- Uploading payload + proto to EC2 ---"
$SCP "$RESULTS_DIR/payload.json" "$SCRIPT_DIR/protos/fraud_detection.proto" "ec2-user@$EC2_IP:$REMOTE_DIR/"

# --- 7. run ghz ---
echo "--- ghz: c=$CONCURRENCY for $DURATION against $NLB_DNS:9090 ---"
$SSH "$GHZ_BIN \
  --proto=$REMOTE_DIR/fraud_detection.proto \
  --call=com.hsbc.fds.FraudDetectionService/CheckTransaction \
  --data-file=$REMOTE_DIR/payload.json \
  --insecure \
  --concurrency=$CONCURRENCY \
  --duration=$DURATION \
  --timeout=10s \
  --skipFirst=10 \
  --count-errors \
  --connections=4 \
  --output=$REMOTE_DIR/report.html \
  --format=html \
  $NLB_DNS:9090" 2>&1 | tee "$RESULTS_DIR/ghz-stdout-$TS.log"

# --- 8. fetch report ---
echo "--- Fetching report ---"
$SCP "ec2-user@$EC2_IP:$REMOTE_DIR/report.html" "$RESULTS_DIR/report-$TS.html" 2>/dev/null || \
  echo "  (no report.html fetched; see ghz-stdout log)"

echo ""
echo "=== Done ==="
echo "  HPA log:   $HPA_LOG"
echo "  ghz stdout: $RESULTS_DIR/ghz-stdout-$TS.log"
echo "  report:    $RESULTS_DIR/report-$TS.html"
echo ""
echo "Dashboard:  https://ap-southeast-1.console.aws.amazon.com/cloudwatch/home?region=ap-southeast-1#dashboards:name=fds-uat"
