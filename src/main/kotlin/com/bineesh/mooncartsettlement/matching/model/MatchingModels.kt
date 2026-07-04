package com.bineesh.mooncartsettlement.matching.model

import java.math.BigDecimal

data class MatchScore(
    val total: BigDecimal,
    val orderReferenceMatch: BigDecimal = BigDecimal.ZERO,
    val yunoIdMatch: BigDecimal = BigDecimal.ZERO,
    val emailMatch: BigDecimal = BigDecimal.ZERO,
    val amountScore: BigDecimal = BigDecimal.ZERO,
    val timingScore: BigDecimal = BigDecimal.ZERO,
)

data class AmountResult(
    val withinMatchTolerance: Boolean,
    val variancePct: BigDecimal,
)

data class MatchCandidate(
    val bankSettlementId: java.util.UUID,
    val yunoTransactionId: java.util.UUID,
    val internalOrderId: java.util.UUID?,
    val score: MatchScore,
)

data class MatchingContext(
    val yunoById: Map<java.util.UUID, com.bineesh.mooncartsettlement.domain.model.YunoTransaction>,
    val bankById: Map<java.util.UUID, com.bineesh.mooncartsettlement.domain.model.BankSettlement>,
    val orderById: Map<java.util.UUID, com.bineesh.mooncartsettlement.domain.model.InternalOrder>,
    val groupByYunoId: MutableMap<java.util.UUID, com.bineesh.mooncartsettlement.domain.model.MatchGroup>,
    val groupByBankId: MutableMap<java.util.UUID, com.bineesh.mooncartsettlement.domain.model.MatchGroup>,
    val groupByOrderId: MutableMap<java.util.UUID, com.bineesh.mooncartsettlement.domain.model.MatchGroup>,
)
