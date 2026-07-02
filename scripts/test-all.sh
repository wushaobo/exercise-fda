#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
EXIT_CODE=0

echo "=== FDS Test Suite ==="

for service_dir in "$REPO_ROOT"/services/*/; do
    service_name=$(basename "$service_dir")
    echo ""
    echo "--- Testing $service_name ---"

    if cd "$service_dir" && gradle test jacocoTestReport; then
        echo "  PASSED: $service_name"
        echo "  Coverage report: services/$service_name/build/reports/jacoco/test/html/index.html"
    else
        echo "  FAILED: $service_name"
        EXIT_CODE=1
    fi
done

echo ""
if [ $EXIT_CODE -eq 0 ]; then
    echo "=== All services passed ==="
else
    echo "=== Some tests failed ==="
fi

exit $EXIT_CODE
