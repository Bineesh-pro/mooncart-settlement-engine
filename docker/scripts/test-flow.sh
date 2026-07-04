#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TESTDATA_DIR="${TESTDATA_DIR:-Dev}"

echo "==> Waiting for API at ${BASE_URL}"
for i in $(seq 1 30); do
  if curl -sf "${BASE_URL}/swagger-ui.html" > /dev/null; then
    break
  fi
  sleep 2
done

echo "==> Creating reconciliation run"
RUN_ID=$(curl -sf -X POST "${BASE_URL}/api/v1/reconciliation/runs" \
  -H 'Content-Type: application/json' \
  -d '{"periodStart":"2026-03-01","periodEnd":"2026-03-30"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

echo "RUN_ID=${RUN_ID}"

echo "==> Ingesting CSV files"
curl -sf -X POST "${BASE_URL}/api/v1/ingest/yuno?runId=${RUN_ID}" \
  -F "file=@${TESTDATA_DIR}/yuno_transactions.csv" | python3 -m json.tool

curl -sf -X POST "${BASE_URL}/api/v1/ingest/bank-settlements?runId=${RUN_ID}" \
  -F "file=@${TESTDATA_DIR}/bank_settlements.csv" | python3 -m json.tool

curl -sf -X POST "${BASE_URL}/api/v1/ingest/orders?runId=${RUN_ID}" \
  -F "file=@${TESTDATA_DIR}/internal_orders.csv" | python3 -m json.tool

echo "==> Executing reconciliation"
curl -sf -X POST "${BASE_URL}/api/v1/reconciliation/runs/${RUN_ID}/execute" | python3 -m json.tool

echo "==> Sample discrepancy query"
curl -sf "${BASE_URL}/api/v1/discrepancies?runId=${RUN_ID}&type=UNMATCHED_YUNO&size=3" | python3 -m json.tool

echo "==> Investigation queue"
curl -sf "${BASE_URL}/api/v1/investigation-queue?runId=${RUN_ID}&limit=5" | python3 -m json.tool

echo "Done. RUN_ID=${RUN_ID}"
