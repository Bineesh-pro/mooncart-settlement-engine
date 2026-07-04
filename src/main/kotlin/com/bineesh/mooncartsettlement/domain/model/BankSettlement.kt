package com.bineesh.mooncartsettlement.domain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "bank_settlements")
class BankSettlement(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "bank_reference_number", nullable = false, length = 128)
    var bankReferenceNumber: String,

    @Column(name = "settlement_date", nullable = false)
    var settlementDate: LocalDate,

    @Column(name = "settled_amount", nullable = false, precision = 19, scale = 4)
    var settledAmount: BigDecimal,

    @Column(nullable = false, length = 3)
    var currency: String,

    @Column(name = "yuno_transaction_id", length = 128)
    var yunoTransactionId: String? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reconciliation_run_id", nullable = false)
    var reconciliationRun: ReconciliationRun,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_group_id")
    var matchGroup: MatchGroup? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)
