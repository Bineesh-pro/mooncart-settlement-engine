package com.bineesh.mooncartsettlement.domain.enums

enum class YunoTransactionStatus {
    AUTHORIZED,
    CAPTURED,
    REFUNDED,
}

enum class PaymentStatus {
    PAID,
    PENDING,
    REFUNDED,
}

enum class ReconciliationRunStatus {
    CREATED,
    INGESTING,
    READY,
    RUNNING,
    COMPLETED,
    FAILED,
}

enum class MatchMethod {
    EXACT_ORDER_REF,
    EXACT_YUNO_ID,
    FUZZY_SCORED,
    ORPHAN_LINK,
}

enum class DiscrepancyType {
    UNMATCHED_YUNO,
    UNMATCHED_SETTLEMENT,
    UNMATCHED_ORDER,
    AMOUNT_MISMATCH,
    TIMING_ANOMALY,
    REFUND_CHARGEBACK_SUSPECTED,
}

enum class DiscrepancySeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

enum class SourceEntityType {
    YUNO_TRANSACTION,
    BANK_SETTLEMENT,
    INTERNAL_ORDER,
    MATCH_GROUP,
}
