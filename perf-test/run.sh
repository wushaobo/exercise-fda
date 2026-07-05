#!/usr/bin/env bash
#
# FDS perf test runner.
#
# Usage:
#   ./run.sh                # all-in-one: prepare + exec + cleanup
#   ./run.sh prepare        # setup payload, Redis denylist, NLB, upload files
#   ./run.sh exec           # run ghz (repeatable with different env vars)
#   ./run.sh cleanup        # tear down NLB, stop HPA watch, remove temp files
#
# Env vars (exec):
#   CONCURRENCY=100  DURATION=3m  N=1000  CONNECTIONS=4  SKIPFIRST=10
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
INFRA_DIR="$REPO_ROOT/infra"
NS="${NAMESPACE:-fds}"
RESULTS_DIR="$SCRIPT_DIR/results"
STATE_FILE="$RESULTS_DIR/.test-state"

mkdir -p "$RESULTS_DIR"

# --- helpers ---------------------------------------------------------

die() { echo "ERROR: $*" >&2; exit 1; }

load_state() {
  [ -f "$STATE_FILE" ] || die "no .test-state found; run './run.sh prepare' first"
  source "$STATE_FILE"
  [ -n "${EC2_IP:-}" ] && [ -n "${KEY_FILE:-}" ] && [ -n "${NLB_DNS:-}" ] \
    || die ".test-state is corrupt; run './run.sh cleanup' and retry"
}

ssh_cmd()  { ssh -i "$KEY_FILE" -o StrictHostKeyChecking=no -o ConnectTimeout=10 ec2-user@"$EC2_IP" "$@"; }
scp_cmd()  { scp -i "$KEY_FILE" -o StrictHostKeyChecking=no "$@"; }

# --- prepare ---------------------------------------------------------

cmd_prepare() {
  [ -f "$STATE_FILE" ] && die ".test-state already exists; run './run.sh cleanup' first"

  local CONCURRENCY="${CONCURRENCY:-100}" DURATION="${DURATION:-3m}" N="${N:-1000}"
  echo "=== prepare: FDS perf test ==="
  echo "  mix: 90/9/1 (clear/suspicious/fraud), N=$N"
  echo ""

  # 0. fetch EC2 IP + ssh key
  echo "--- Fetching ghz runner EC2 IP + key ---"
  local EC2_IP KEY_FILE
  EC2_IP="$(terraform -chdir="$INFRA_DIR" output -raw ghz_runner_public_ip)"
  KEY_FILE="/tmp/ghz-key-$$"
  terraform -chdir="$INFRA_DIR" output -raw ghz_runner_private_key > "$KEY_FILE"
  chmod 600 "$KEY_FILE"
  echo "  EC2: $EC2_IP"

  # 1. generate payload
  echo "--- Generating 90/9/1 payload array ($N) ---"
  python3 "$SCRIPT_DIR/gen-payload.py" "$N" > "$RESULTS_DIR/payload.json"
  PAYLOAD_COUNT=$(python3 -c "import json; print(len(json.load(open('$RESULTS_DIR/payload.json'))))")
  echo "  $PAYLOAD_COUNT requests generated"

  # 2. seed denylist
  echo "--- Seeding ElastiCache denylist (account-blocked-1,2) ---"
  local REDIS_HOST REDIS_PASS
  REDIS_HOST="$(terraform -chdir="$INFRA_DIR" output -raw redis_endpoint)"
  REDIS_PASS="$(kubectl -n "$NS" get secret fds-secret -o jsonpath='{.data.redis-password}' 2>/dev/null | base64 -d 2>/dev/null || true)"
  [ -z "$REDIS_PASS" ] && echo "  WARN: could not read redis password; fraud path may not trigger"
  kubectl -n "$NS" run redis-seed --rm -i --restart=Never --image=redis:alpine -- \
    redis-cli --tls --user default -a "$REDIS_PASS" -h "$REDIS_HOST" \
    SET fds:denylist "account-blocked-1,account-blocked-2" >/dev/null 2>&1 || \
    echo "  WARN: denylist seed failed (continuing)"
  echo "  waiting 65s for rule-check-worker DenylistCache refresh..."
  sleep 65

  # 3. create NLB
  echo "--- Applying sync-facade internal NLB ---"
  kubectl -n "$NS" apply -f "$SCRIPT_DIR/nlb-service.yaml"
  echo "  waiting for NLB DNS..."
  local NLB_DNS=""
  for i in $(seq 1 40); do
    NLB_DNS="$(kubectl -n "$NS" get svc sync-facade-nlb -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || true)"
    [ -n "$NLB_DNS" ] && break
    sleep 5
  done
  [ -z "$NLB_DNS" ] && die "NLB DNS not ready"
  echo "  NLB: $NLB_DNS:9090"

  echo "  waiting for NLB DNS to resolve on EC2..."
  for i in $(seq 1 60); do
    ssh_cmd "resolvectl flush-caches 2>/dev/null || systemd-resolve --flush-caches 2>/dev/null; getent hosts $NLB_DNS" >/dev/null 2>&1 && { echo "  DNS resolved on EC2 (after $((i*5))s)"; break; }
    sleep 5
  done
  ssh_cmd "getent hosts $NLB_DNS" >/dev/null 2>&1 || die "NLB DNS not resolving on EC2 after 300s"

  # 4. upload payload + proto
  echo "--- Uploading payload + proto to EC2 ---"
  scp_cmd "$RESULTS_DIR/payload.json" "$SCRIPT_DIR/protos/fraud_detection.proto" "ec2-user@$EC2_IP:$REMOTE_DIR/"

  # save state
  cat > "$STATE_FILE" <<EOF
EC2_IP=$EC2_IP
KEY_FILE=$KEY_FILE
NLB_DNS=$NLB_DNS
REMOTE_DIR=$REMOTE_DIR
EOF
  echo ""
  echo "=== prepare done ==="
  echo "  state: $STATE_FILE"
  echo "  next:  CONCURRENCY=100 DURATION=3m ./run.sh exec"
}

# --- exec ------------------------------------------------------------

cmd_exec() {
  load_state

  local CONCURRENCY="${CONCURRENCY:-100}"
  local DURATION="${DURATION:-3m}"
  local N="${N:-1000}"
  local CONNECTIONS="${CONNECTIONS:-4}"
  local SKIPFIRST="${SKIPFIRST:-10}"
  local GHZ_BIN="${GHZ_BIN:-ghz}"
  local TS="$(date +%Y%m%d-%H%M%S)"
  local HPA_LOG="$RESULTS_DIR/hpa-$TS.log"

  echo "=== exec: ghz test ==="
  echo "  concurrency: $CONCURRENCY, duration: $DURATION, connections: $CONNECTIONS"
  echo "  NLB: $NLB_DNS:9090"
  echo ""

  # regenerate payload if N changed vs state
  local REGEN="${REGEN:-}"
  if [ -n "$REGEN" ] || [ ! -f "$RESULTS_DIR/payload.json" ]; then
    echo "--- Regenerating payload (N=$N) ---"
    python3 "$SCRIPT_DIR/gen-payload.py" "$N" > "$RESULTS_DIR/payload.json"
    scp_cmd "$RESULTS_DIR/payload.json" "ec2-user@$EC2_IP:$REMOTE_DIR/"
  fi

  # HPA watch (background)
  echo "--- HPA watch → $HPA_LOG ---"
  kubectl -n "$NS" get hpa sync-facade -w > "$HPA_LOG" 2>&1 &
  local HPA_PID=$!

  # verify ghz
  echo "--- Checking ghz on EC2 ($GHZ_BIN) ---"
  for i in $(seq 1 30); do
    if ssh_cmd "$GHZ_BIN --version" >/dev/null 2>&1; then echo "  ghz ready"; break; fi
    sleep 5
  done
  ssh_cmd "$GHZ_BIN --version" >/dev/null 2>&1 || die "ghz not found at '$GHZ_BIN' on EC2"

  # run ghz
  local CONNS=$(( CONCURRENCY < CONNECTIONS ? CONCURRENCY : CONNECTIONS ))
  echo "--- ghz: c=$CONCURRENCY for $DURATION against $NLB_DNS:9090 ---"
  ssh_cmd "$GHZ_BIN \
    --proto=$REMOTE_DIR/fraud_detection.proto \
    --call=com.hsbc.fds.FraudDetectionService/CheckTransaction \
    --data-file=$REMOTE_DIR/payload.json \
    --insecure \
    --concurrency=$CONCURRENCY \
    --duration=$DURATION \
    --timeout=10s \
    --skipFirst=$SKIPFIRST \
    --count-errors \
    --connections=$CONNS \
    --output=$REMOTE_DIR/report.html \
    --format=html \
    $NLB_DNS:9090" 2>&1 | tee "$RESULTS_DIR/ghz-stdout-$TS.log"

  # fetch report
  echo "--- Fetching report ---"
  scp_cmd "ec2-user@$EC2_IP:$REMOTE_DIR/report.html" "$RESULTS_DIR/report-$TS.html" 2>/dev/null || \
    echo "  (no report.html fetched; see ghz-stdout log)"

  # stop HPA watch
  kill "${HPA_PID:-}" 2>/dev/null || true

  echo ""
  echo "=== exec done ==="
  echo "  HPA log:    $HPA_LOG"
  echo "  report:     $RESULTS_DIR/report-$TS.html"
  echo "  ghz stdout: $RESULTS_DIR/ghz-stdout-$TS.log"
  echo "  Dashboard:  https://ap-southeast-1.console.aws.amazon.com/cloudwatch/home?region=ap-southeast-1#dashboards:name=fds-uat"
}

# --- cleanup ----------------------------------------------------------

cmd_cleanup() {
  echo "=== cleanup ==="

  if [ -f "$STATE_FILE" ]; then
    load_state
    rm -f "$KEY_FILE"
    echo "  removed key: $KEY_FILE"
  else
    # best-effort: clean up NLB even without state
    echo "  (no .test-state; cleaning NLB best-effort)"
  fi

  kubectl -n "$NS" delete -f "$SCRIPT_DIR/nlb-service.yaml" --ignore-not-found >/dev/null 2>&1 || true
  kubectl -n "$NS" delete pod redis-seed --ignore-not-found >/dev/null 2>&1 || true
  rm -f "$STATE_FILE"
  echo "  removed: NLB, redis-seed pod, .test-state"
  echo "=== cleanup done ==="
}

# --- dispatch ---------------------------------------------------------

REMOTE_DIR="/home/ec2-user"
CMD="${1:-all}"

case "$CMD" in
  prepare) cmd_prepare ;;
  exec)    cmd_exec ;;
  cleanup) cmd_cleanup ;;
  all)     cmd_prepare && cmd_exec && cmd_cleanup ;;
  *)       die "unknown command: $CMD (valid: prepare, exec, cleanup)" ;;
esac
