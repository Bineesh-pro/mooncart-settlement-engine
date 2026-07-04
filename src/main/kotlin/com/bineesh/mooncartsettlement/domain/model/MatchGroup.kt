package com.bineesh.mooncartsettlement.domain.model

import com.bineesh.mooncartsettlement.domain.enums.MatchMethod
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "match_groups")
class MatchGroup(
    @Id
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reconciliation_run_id", nullable = false)
    var reconciliationRun: ReconciliationRun,

    @Column(name = "confidence_score", precision = 5, scale = 2)
    var confidenceScore: BigDecimal? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "match_method", length = 32)
    var matchMethod: MatchMethod? = null,

    @Column(name = "amount_variance_pct", precision = 10, scale = 6)
    var amountVariancePct: BigDecimal? = null,

    @Column(name = "settlement_delay_days")
    var settlementDelayDays: Int? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)
