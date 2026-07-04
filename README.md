# MoonCart Settlement Matching Engine

A Spring Boot Kotlin backend service that reconciles payment data across three sources — **Yuno transaction logs**, **bank settlement files**, and **MoonCart internal orders** — even when identifiers do not align perfectly. Finance teams can run batch reconciliation jobs and investigate discrepancies through a REST API.

---

## Table of Contents

1. [Problem Statement](#1-problem-statement)
2. [Solution](#2-solution)
3. [Application Details](#3-application-details)
4. [Technical Details](#4-technical-details)
5. [API Reference](#5-api-reference)
6. [How to Run the Project](#6-how-to-run-the-project)
7. [How to Test the Whole Flow](#7-how-to-test-the-whole-flow)
8. [Expected Results](#8-expected-results)

---

## 1. Problem Statement

MoonCart processes payments through Yuno and receives bank settlements on a delayed schedule. Internal order records form a third view of the same transactions. Reconciliation is difficult because:

| Challenge | Example |
|-----------|---------|
| **Different IDs per system** | Yuno transaction ID ≠ bank reference ≠ order ID |
| **Amount differences** | Settlement is net of fees, FX conversion, or partial funding |
| **Timing mismatches** | Settlements arrive 1–5 days after the transaction; timezones differ |
| **Partial settlements** | Disputes, holds, refunds, or chargebacks reduce settled amounts |
| **Missing links in bank files** | Bank CSV sometimes omits the Yuno transaction ID |

The finance team needs a service that can:

- Ingest all three data sources for a period (1000+ transactions)
- Match records intelligently using multiple signals
- Classify issues into clear discrepancy types
- Expose queryable APIs for investigation and reporting

---

## 2. Solution

### 2.1 Approach

The service uses a **batch reconciliation model**:

```
Ingest (3 sources) → Execute matching → Persist results → Investigate via API
```

Each reconciliation **run** represents one period (e.g. March 2026). All ingested records are scoped to a run. Matching is executed on demand and produces:

- **Match groups** — linked Yuno + Bank + Order records
- **Discrepancies** — typed issues for finance investigation
- **Reconciliation report** — summary counts and unmatched amounts

Design principles:

- **Phased matching** — exact links first, then fuzzy scoring, then orphan linking
- **Two amount tolerances** — a looser tolerance for *linking*, a tighter one for *reconciliation*
- **Configurable thresholds** — all tolerances live in `application.yaml`
- **Idempotent re-runs** — executing matching again clears prior match groups and discrepancies for that run

### 2.2 Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        REST API Layer                           │
│  IngestController │ ReconciliationController │ Investigation   │
└────────────┬────────────────────┬───────────────────┬───────────┘
             │                    │                   │
┌────────────▼────────┐  ┌────────▼────────┐  ┌──────▼──────────┐
│   IngestService     │  │ Reconciliation  │  │ Investigation   │
│   CsvIngestParser   │  │ Service         │  │ Service         │
└────────────┬────────┘  └────────┬────────┘  └─────────────────┘
             │                    │
             │           ┌──────────▼──────────┐
             │           │   MatchingEngine    │
             │           │  ┌────────────────┐ │
             │           │  │ ExactMatcher   │ │
             │           │  │ FuzzyMatcher   │ │
             │           │  │ MatchScorer    │ │
             │           │  │ Discrepancy    │ │
             │           │  │ Classifier     │ │
             │           │  └────────────────┘ │
             │           └──────────┬──────────┘
             │                      │
┌────────────▼──────────────────────▼──────────────────────────────┐
│                     PostgreSQL (Flyway migrations)               │
│  yuno_transactions │ bank_settlements │ internal_orders          │
│  match_groups │ discrepancies │ reconciliation_runs              │
└──────────────────────────────────────────────────────────────────┘
```

### 2.3 Core Matching Logic

Matching runs in **three phases** inside `MatchingEngine`.

#### Phase 1 — Exact matching (`ExactMatcher`)

High-confidence links using strong identifiers:

| Link | Condition | Confidence |
|------|-----------|------------|
| Yuno ↔ Order | `yuno.orderReference` = `order.orderId` (case-insensitive) | ~92 |
| Bank ↔ Yuno | `bank.yunoTransactionId` = `yuno.yunoTransactionId` | ~95 |

#### Phase 2 — Fuzzy matching (`FuzzyMatcher`)

For unmatched bank settlements (especially when `yunoTransactionId` is missing):

**Pre-filter candidates** (same currency, settlement within 0–5 days of transaction, amount within 10%):

**Score each candidate (0–100):**

| Signal | Weight | Rule |
|--------|--------|------|
| Order reference match | 30 | Yuno order ref matches linked order |
| Yuno ID match | 30 | Bank yuno ID matches Yuno record |
| Email match | 15 | Yuno email matches order email |
| Amount proximity | 15 | `15 × (1 − variance / 5%)` |
| Timing proximity | 10 | `10 × (1 − delayDays / 5)` |

Pairs with score ≥ **70** (configurable) are assigned using **greedy best-first** assignment to avoid double-matching.

#### Phase 3 — Orphan linking (`FuzzyMatcher`)

For remaining unmatched Yuno and Order records:

- Same `customerEmail` and `currency`
- Amount within 5% tolerance
- Timestamps within ±24 hours
- Lower confidence (~70), flagged for audit

#### Amount comparison (`MatchScorer`)

Two separate tolerances:

| Tolerance | Default | Purpose |
|-----------|---------|---------|
| **Match tolerance** | 5% (+ absolute: USD/PHP ±0.01, IDR ±100) | "Could these records belong together?" |
| **Discrepancy threshold** | 2% | "Do the amounts reconcile after linking?" |

A transaction can be **linked** at 4% variance but still produce an `AMOUNT_MISMATCH` discrepancy.

#### Timing rules

- All timestamps normalized to **UTC** on ingest
- Valid settlement window: **0–5 days** after transaction date
- Delays **> 3 days** on linked records → `TIMING_ANOMALY`

### 2.4 Discrepancy Detection and Classification

After matching, `DiscrepancyClassifier` emits typed issues:

| Type | Condition | Severity basis |
|------|-----------|----------------|
| `UNMATCHED_YUNO` | Yuno status = CAPTURED, no bank linked | Amount (USD equivalent) |
| `UNMATCHED_SETTLEMENT` | Bank record with no Yuno linked | Amount |
| `UNMATCHED_ORDER` | Order status = PAID, no Yuno linked | Amount |
| `AMOUNT_MISMATCH` | Linked, variance > 2% | MEDIUM |
| `TIMING_ANOMALY` | Linked, settlement delay > 3 days | LOW |
| `REFUND_CHARGEBACK_SUSPECTED` | Refund status, negative settlement, or >10% shortfall | HIGH |

**Refund/chargeback heuristics:**

- Yuno status = `REFUNDED` → reason `REFUND`
- Negative settlement on same date/currency → reason `CHARGEBACK`
- ~50% settled amount → reason `PARTIAL_SETTLEMENT`

**Investigation priority** (stretch goal, implemented):

```
priority = (ageDays × 0.4) + (normalizedAmount × 0.4) + (merchantImpact × 0.2)
```

Older, larger, and merchant-heavy issues rank higher in the investigation queue.

### 2.5 Reconciliation Reports

Both `POST .../execute` and `GET .../report` return the same report structure:

```json
{
  "runId": "uuid",
  "summary": {
    "totalYuno": 1050,
    "totalBank": 1005,
    "totalOrders": 1050,
    "fullyMatched": 850,
    "unmatchedYuno": 45,
    "unmatchedSettlement": 30,
    "unmatchedOrder": 20,
    "amountDiscrepancies": 55,
    "timingAnomalies": 20,
    "refundChargebackSuspected": 10,
    "totalUnmatchedAmount": {
      "USD": 1200.50,
      "IDR": 5000000,
      "PHP": 85000
    }
  },
  "generatedAt": "2026-07-04T09:00:00Z"
}
```

**Fully matched** = all 3 sources linked in one match group + amount variance ≤ 2%.

---

## 3. Application Details

### 3.1 Package Structure

```
com.bineesh.mooncartsettlement
├── domain/           # JPA entities, enums, repositories
├── ingestion/        # CSV parser and ingest endpoints
├── matching/         # MatchScorer, ExactMatcher, FuzzyMatcher, Classifier, Engine
├── reconciliation/   # Run lifecycle, report building
├── api/              # Discrepancy investigation endpoints
├── config/           # MatchingProperties, OpenAPI, exception handler
└── testdata/         # Test CSV generator (seeded Random(42))
```

### 3.2 Data Model

| Entity | Key fields |
|--------|------------|
| `ReconciliationRun` | period start/end, status, timestamps |
| `YunoTransaction` | yunoTransactionId, amount, currency, status, merchantId, email, orderReference |
| `BankSettlement` | bankReferenceNumber, settlementDate, settledAmount, yunoTransactionId (nullable) |
| `InternalOrder` | orderId, email, orderAmount, paymentStatus |
| `MatchGroup` | confidenceScore, matchMethod, amountVariancePct, settlementDelayDays |
| `Discrepancy` | type, severity, sourceEntity, details (JSON), investigationPriority |

### 3.3 Supported Currencies

`IDR`, `PHP`, `USD` — validated on ingest.

### 3.4 Ingestion Format

All data ingestion uses **CSV file upload** (multipart form). Expected column headers:

```
# yuno_transactions.csv
yuno_transaction_id,timestamp,amount,currency,status,merchant_id,customer_email,order_reference

# bank_settlements.csv
bank_reference_number,settlement_date,settled_amount,currency,yuno_transaction_id

# internal_orders.csv
order_id,customer_email,order_amount,currency,timestamp,payment_status
```

Ingest rules:

- Timestamps parsed and stored in UTC
- Duplicate natural keys within a run are rejected (`accepted`, `rejected`, `duplicates` in response)
- Emails normalized to lowercase

### 3.5 Configurable Matching Parameters

```yaml
settlement:
  matching:
    amount-tolerance-pct: 0.05              # 5% linking tolerance
    amount-discrepancy-threshold-pct: 0.02  # 2% reconciliation threshold
    amount-absolute-tolerance:
      USD: 0.01
      PHP: 0.01
      IDR: 100
    settlement-delay-max-days: 5
    timing-anomaly-threshold-days: 3
    fuzzy-match-min-score: 70
    max-candidates-per-record: 20
    orphan-link-max-hours: 24
```

---

## 4. Technical Details

| Component | Technology |
|-----------|------------|
| Language | Kotlin 2.3 |
| Framework | Spring Boot 4.1 |
| JVM | Java 25 |
| Database | PostgreSQL 18 (port 5462) |
| ORM | Spring Data JPA / Hibernate 7 |
| Migrations | Flyway (`db/migration/V1__init.sql`) |
| API docs | SpringDoc OpenAPI 2.8 (`/swagger-ui.html`) |
| CSV parsing | Apache Commons CSV |
| Testing | JUnit 5, H2 (in-memory, PostgreSQL mode) |
| Build | Gradle 9.5 |

**Database:** `jdbc:postgresql://localhost:5462/mooncartsettlement`  
**Credentials:** `postgres` / `postgres`

Flyway runs on startup; Hibernate `ddl-auto: validate` ensures entities match the schema.

---

## 5. API Reference

**Base URL:** `http://localhost:8080`  
**Swagger UI:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)  
**OpenAPI JSON:** [http://localhost:8080/api-docs](http://localhost:8080/api-docs)

---

### 5.1 Reconciliation Runs

#### Create a reconciliation run

Starts a new batch job for a date range. Save the returned `id` as `RUN_ID`.

```bash
curl -s -X POST http://localhost:8080/api/v1/reconciliation/runs \
  -H 'Content-Type: application/json' \
  -d '{
    "periodStart": "2026-03-01",
    "periodEnd": "2026-03-30"
  }'
```

**Response:**
```json
{
  "id": "96d030b2-c66f-46ad-b1cc-26760770b3c7",
  "periodStart": "2026-03-01",
  "periodEnd": "2026-03-30",
  "status": "CREATED"
}
```

```bash
export RUN_ID="96d030b2-c66f-46ad-b1cc-26760770b3c7"
```

---

#### Execute matching

Runs the matching engine and returns the reconciliation report.

```bash
curl -s -X POST "http://localhost:8080/api/v1/reconciliation/runs/${RUN_ID}/execute"
```

**Use when:** all three sources have been ingested and you want to produce match groups and discrepancies.

---

#### Get reconciliation report

Returns the same report structure as execute, computed from persisted data.

```bash
curl -s "http://localhost:8080/api/v1/reconciliation/runs/${RUN_ID}/report"
```

**Use when:** re-fetching a report without re-running matching.

---

### 5.2 Data Ingestion (CSV upload)

All ingest endpoints require `runId` as a query parameter and accept a **CSV file** via multipart form field `file`.

#### Ingest Yuno transactions

```bash
curl -s -X POST "http://localhost:8080/api/v1/ingest/yuno?runId=${RUN_ID}" \
  -F "file=@build/testdata/yuno_transactions.csv"
```

**CSV status values:** `AUTHORIZED`, `CAPTURED`, `REFUNDED`

---

#### Ingest bank settlements

```bash
curl -s -X POST "http://localhost:8080/api/v1/ingest/bank-settlements?runId=${RUN_ID}" \
  -F "file=@build/testdata/bank_settlements.csv"
```

The `yuno_transaction_id` column is optional — leave blank to test fuzzy matching.

---

#### Ingest internal orders

```bash
curl -s -X POST "http://localhost:8080/api/v1/ingest/orders?runId=${RUN_ID}" \
  -F "file=@build/testdata/internal_orders.csv"
```

**CSV payment status values:** `PAID`, `PENDING`, `REFUNDED`

**Ingest response (all sources):**
```json
{
  "accepted": 1050,
  "rejected": 0,
  "duplicates": 0,
  "errors": []
}
```

---

### 5.3 Discrepancy Investigation

#### Query discrepancies (paginated, filterable)

```bash
curl -s "http://localhost:8080/api/v1/discrepancies?runId=${RUN_ID}&page=0&size=20"
```

**Filter examples:**

```bash
# Unmatched Yuno transactions (potential missing funds)
curl -s "http://localhost:8080/api/v1/discrepancies?runId=${RUN_ID}&type=UNMATCHED_YUNO"

# Amount mismatches above $1000 USD equivalent
curl -s "http://localhost:8080/api/v1/discrepancies?runId=${RUN_ID}&type=AMOUNT_MISMATCH&currency=USD&minAmount=1000"

# By merchant and date range
curl -s "http://localhost:8080/api/v1/discrepancies?runId=${RUN_ID}&merchantId=MCH-1&from=2026-03-01&to=2026-03-30"
```

| Parameter | Description |
|-----------|-------------|
| `runId` | Scope to a reconciliation run |
| `type` | `UNMATCHED_YUNO`, `UNMATCHED_SETTLEMENT`, `UNMATCHED_ORDER`, `AMOUNT_MISMATCH`, `TIMING_ANOMALY`, `REFUND_CHARGEBACK_SUSPECTED` |
| `currency` | `IDR`, `PHP`, `USD` |
| `merchantId` | Merchant filter |
| `minAmount` | Minimum amount |
| `from` / `to` | Date range on discrepancy creation (`YYYY-MM-DD`) |
| `page` | Page number (default 0) |
| `size` | Page size (default 50) |

---

#### Get single discrepancy detail

```bash
curl -s "http://localhost:8080/api/v1/discrepancies/<DISCREPANCY_UUID>"
```

Returns full context: type, severity, amount, merchant, `details` JSON, investigation priority.

---

#### Get cross-source match detail

Side-by-side view of Yuno + Bank + Order for a match group.

```bash
curl -s "http://localhost:8080/api/v1/matches/<MATCH_GROUP_UUID>"
```

**Use when:** a match exists but you need to compare amounts, timestamps, confidence, and delay across all three sources.

---

### 5.4 Statistics

#### Summary statistics

```bash
curl -s "http://localhost:8080/api/v1/statistics/summary?runId=${RUN_ID}"
```

Returns total discrepancies, counts by type/severity, unmatched amounts by currency.

---

#### Trend over time

```bash
curl -s "http://localhost:8080/api/v1/statistics/trends?runId=${RUN_ID}&from=2026-03-01&to=2026-03-30"
```

Returns daily discrepancy counts grouped by type.

---

### 5.5 Investigation Queue

Prioritized list of discrepancies for finance triage.

```bash
curl -s "http://localhost:8080/api/v1/investigation-queue?runId=${RUN_ID}&limit=50"
```

Ranked by age, amount, and merchant impact.

---

### 5.6 API Summary Table

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/reconciliation/runs` | Create reconciliation run |
| POST | `/api/v1/ingest/yuno` | Ingest Yuno transactions (CSV upload) |
| POST | `/api/v1/ingest/bank-settlements` | Ingest bank settlements (CSV upload) |
| POST | `/api/v1/ingest/orders` | Ingest internal orders (CSV upload) |
| POST | `/api/v1/reconciliation/runs/{runId}/execute` | Run matching engine |
| GET | `/api/v1/reconciliation/runs/{runId}/report` | Get reconciliation report |
| GET | `/api/v1/discrepancies` | Search/filter discrepancies |
| GET | `/api/v1/discrepancies/{id}` | Get discrepancy detail |
| GET | `/api/v1/matches/{matchGroupId}` | Cross-source match view |
| GET | `/api/v1/statistics/summary` | Aggregate statistics |
| GET | `/api/v1/statistics/trends` | Daily trend by type |
| GET | `/api/v1/investigation-queue` | Prioritized investigation list |

---

## 6. How to Run the Project

### Prerequisites

- **Java 25**
- **PostgreSQL** running on `localhost:5462`
- Database **`mooncartsettlement`** created (user/password: `postgres`)

Create the database if it does not exist:

```bash
PGPASSWORD=postgres psql -h localhost -p 5462 -U postgres \
  -c "CREATE DATABASE mooncartsettlement;"
```

### Start the application

```bash
./gradlew bootRun
```

The app starts on **port 8080**. Flyway applies migrations automatically on first run.

Verify:

```bash
curl -s http://localhost:8080/swagger-ui.html -o /dev/null -w "%{http_code}\n"
# Expected: 200
```

### Generate test data (optional)

Produces 1050+ rows of CSV test data in `build/testdata/`:

```bash
SPRING_APPLICATION_JSON='{"settlement":{"generate-test-data":true}}' ./gradlew bootRun
```

Generated files:

| File | Rows | Description |
|------|------|-------------|
| `yuno_transactions.csv` | 1050 | Yuno payment logs |
| `bank_settlements.csv` | ~1005 | Bank settlement records |
| `internal_orders.csv` | 1050 | MoonCart orders |

Data spans 30 days (2026-03-01 to 2026-03-30), three currencies (IDR ~40%, PHP ~35%, USD ~25%), with ~15% injected problem cases.

---

## 7. How to Test the Whole Flow

### Step 1 — Start the app and generate test data

```bash
./gradlew bootRun
# In another terminal:
SPRING_APPLICATION_JSON='{"settlement":{"generate-test-data":true}}' ./gradlew bootRun
```

### Step 2 — Create a reconciliation run

```bash
RUN_ID=$(curl -s -X POST http://localhost:8080/api/v1/reconciliation/runs \
  -H 'Content-Type: application/json' \
  -d '{"periodStart":"2026-03-01","periodEnd":"2026-03-30"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

echo "RUN_ID=${RUN_ID}"
```

### Step 3 — Ingest all three sources

```bash
curl -s -X POST "http://localhost:8080/api/v1/ingest/yuno?runId=${RUN_ID}" \
  -F "file=@build/testdata/yuno_transactions.csv" | python3 -m json.tool

curl -s -X POST "http://localhost:8080/api/v1/ingest/bank-settlements?runId=${RUN_ID}" \
  -F "file=@build/testdata/bank_settlements.csv" | python3 -m json.tool

curl -s -X POST "http://localhost:8080/api/v1/ingest/orders?runId=${RUN_ID}" \
  -F "file=@build/testdata/internal_orders.csv" | python3 -m json.tool
```

Each response should show `"accepted"` ≥ 1000 (bank slightly fewer due to edge cases).

### Step 4 — Execute matching

```bash
curl -s -X POST "http://localhost:8080/api/v1/reconciliation/runs/${RUN_ID}/execute" \
  | python3 -m json.tool
```

### Step 5 — Verify report matches execute output

```bash
curl -s "http://localhost:8080/api/v1/reconciliation/runs/${RUN_ID}/report" \
  | python3 -m json.tool
```

### Step 6 — Investigate discrepancies

```bash
# Unmatched Yuno (missing settlements)
curl -s "http://localhost:8080/api/v1/discrepancies?runId=${RUN_ID}&type=UNMATCHED_YUNO&size=5" \
  | python3 -m json.tool

# Amount mismatches
curl -s "http://localhost:8080/api/v1/discrepancies?runId=${RUN_ID}&type=AMOUNT_MISMATCH&size=5" \
  | python3 -m json.tool

# Investigation queue
curl -s "http://localhost:8080/api/v1/investigation-queue?runId=${RUN_ID}&limit=10" \
  | python3 -m json.tool

# Summary stats
curl -s "http://localhost:8080/api/v1/statistics/summary?runId=${RUN_ID}" \
  | python3 -m json.tool
```

### Step 7 — Run automated tests

```bash
./gradlew test
```

Tests include:

| Test | Validates |
|------|-----------|
| `MatchScorerTest` | Amount/timing tolerance edge cases |
| `ExactMatcherTest` | Order ref and Yuno ID linking |
| `MatchingEngineIntegrationTest` | Full pipeline on 1050-row dataset; execute vs get report consistency |

---

## 8. Expected Results

When running against the generated test dataset (~1050 transactions, ~15% problem cases), expect approximate ranges:

| Metric | Expected range | Notes |
|--------|----------------|-------|
| `totalYuno` | 1050 | All generated Yuno records |
| `totalOrders` | 1050 | All generated orders |
| `totalBank` | ~950–1010 | Fewer due to unmatched-Yuno and orphan settlement edge cases |
| `fullyMatched` | ~800–900 | All 3 sources linked, amount variance ≤ 2% |
| `unmatchedYuno` | ~40–60 | Yuno captured but no settlement |
| `unmatchedSettlement` | ~20–40 | Bank money with no Yuno match |
| `unmatchedOrder` | ~10–20 | Paid orders with no Yuno link |
| `amountDiscrepancies` | ~30–50 | Linked but 2–5% amount difference |
| `timingAnomalies` | ~15–25 | 3–5 day settlement delays |
| `refundChargebackSuspected` | ~5–15 | Refunds and partial settlements |
| `totalUnmatchedAmount` | Non-zero per currency | Sum of unmatched source amounts |

### What each outcome means

| Outcome | Finance action |
|---------|----------------|
| **Fully matched** | No action needed |
| **Unmatched Yuno** | Investigate missing settlement / missing funds |
| **Unmatched settlement** | Investigate duplicate funding or data error |
| **Unmatched order** | Investigate payment pipeline failure |
| **Amount mismatch** | Review fees, FX, or partial settlement |
| **Timing anomaly** | Informational — settlement delayed but linked |
| **Refund/chargeback suspected** | Verify refund or dispute handling |

### Edge cases covered in test data

- Yuno exists but no bank settlement (~50 records)
- Bank settlement with no Yuno match (~30 records)
- Amount off by 2–5% after linking (~40 records)
- 3–5 day settlement delays (~20 records)
- Refunds and chargebacks (~10 records)
- Bank file missing `yunoTransactionId` (forces fuzzy match)
- 5–10 severe cases: large amounts, old dates, fully unmatched

---

## License

Internal MoonCart project — not for external distribution.
