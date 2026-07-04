package com.bineesh.mooncartsettlement.domain.model

import com.bineesh.mooncartsettlement.domain.enums.YunoTransactionStatus
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
@Table(name = "yuno_transactions")
class YunoTransaction(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "yuno_transaction_id", nullable = false, length = 128)
    var yunoTransactionId: String,

    @Column(nullable = false)
    var timestamp: Instant,

    @Column(nullable = false, precision = 19, scale = 4)
    var amount: BigDecimal,

    @Column(nullable = false, length = 3)
    var currency: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: YunoTransactionStatus,

    @Column(name = "merchant_id", nullable = false, length = 64)
    var merchantId: String,

    @Column(name = "customer_email", nullable = false)
    var customerEmail: String,

    @Column(name = "order_reference", nullable = false, length = 128)
    var orderReference: String,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reconciliation_run_id", nullable = false)
    var reconciliationRun: ReconciliationRun,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_group_id")
    var matchGroup: MatchGroup? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)
