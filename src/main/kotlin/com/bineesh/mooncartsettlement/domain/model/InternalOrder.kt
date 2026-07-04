package com.bineesh.mooncartsettlement.domain.model

import com.bineesh.mooncartsettlement.domain.enums.PaymentStatus
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
@Table(name = "internal_orders")
class InternalOrder(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "order_id", nullable = false, length = 128)
    var orderId: String,

    @Column(name = "customer_email", nullable = false)
    var customerEmail: String,

    @Column(name = "order_amount", nullable = false, precision = 19, scale = 4)
    var orderAmount: BigDecimal,

    @Column(nullable = false, length = 3)
    var currency: String,

    @Column(nullable = false)
    var timestamp: Instant,

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 32)
    var paymentStatus: PaymentStatus,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reconciliation_run_id", nullable = false)
    var reconciliationRun: ReconciliationRun,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_group_id")
    var matchGroup: MatchGroup? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)
