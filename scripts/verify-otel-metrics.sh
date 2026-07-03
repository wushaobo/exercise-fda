#!/usr/bin/env bash
#
# Verify the OTel Collector is receiving metrics from sync-facade / rule-check-worker.
# Polls the collector logs for OTLP metric payload markers (Metric #N / ScopeMetrics /
# ResourceMetrics) up to a timeout, then exits 0 on success / 1 on timeout.
#
# Re-runnable: safe to invoke repeatedly against an existing k3d cluster.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
NAMESPACE="${NAMESPACE:-fds}"
COLLECTOR_SELECTOR="${COLLECTOR_SELECTOR:-app=otel-collector}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-120}"
POLL_INTERVAL="${POLL_INTERVAL:-5}"

# Pattern the Collector debug exporter prints when it flushes a metrics batch.
# Includes the dense data-point line markers, not just the per-metric header,
# so a --tail window landing mid-dump still matches reliably.
METRIC_PATTERN='Metric #[0-9]+|ScopeMetrics|ResourceMetrics|NumberDataPoints|HistogramDataPoints|SumDataPoints|GaugeDataPoints'

echo "=== OTel Collector metrics check ==="
echo "  namespace:  $NAMESPACE"
echo "  selector:   $COLLECTOR_SELECTOR"
echo "  timeout:    ${TIMEOUT_SECONDS}s (poll every ${POLL_INTERVAL}s)"

# Sanity: the collector pod must exist.
if ! kubectl -n "$NAMESPACE" get pods -l "$COLLECTOR_SELECTOR" -o name 2>/dev/null | grep -q .; then
    echo "  Collector pod not found under selector '$COLLECTOR_SELECTOR'." >&2
    exit 1
fi

elapsed=0
while [ "$elapsed" -lt "$TIMEOUT_SECONDS" ]; do
    # grep -c (not -q): it reads all input to count, so it never exits early and
    # never SIGPIPEs the upstream kubectl under `set -o pipefail` (which `grep -q`
    # would, returning a false 141). `|| true` swallows grep's exit-1 on no match.
    hits=$(kubectl -n "$NAMESPACE" logs -l "$COLLECTOR_SELECTOR" --tail=1000 2>/dev/null \
        | grep -cE "$METRIC_PATTERN" || true)
    if [ "${hits:-0}" -gt 0 ]; then
        echo "  Metrics received: OK (after ${elapsed}s; ${hits} matching lines)"
        echo "  --- collector metric dump (sample) ---"
        kubectl -n "$NAMESPACE" logs -l "$COLLECTOR_SELECTOR" --tail=1000 2>/dev/null \
            | grep -E "Metric #[0-9]+|-> Name:" | tail -n 12 | sed 's/^/    /'
        exit 0
    fi
    sleep "$POLL_INTERVAL"
    elapsed=$((elapsed + POLL_INTERVAL))
done

echo "  Metrics received: NONE after ${TIMEOUT_SECONDS}s" >&2
echo "  --- collector log tail (last 40 lines) ---" >&2
kubectl -n "$NAMESPACE" logs -l "$COLLECTOR_SELECTOR" --tail=40 2>&1 | sed 's/^/    /' >&2 || true
exit 1
