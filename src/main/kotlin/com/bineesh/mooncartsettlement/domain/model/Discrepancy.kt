package com.bineesh.mooncartsettlement.domain.model

import com.bineesh.mooncartsettlement.domain.enums.DiscrepancySeverity
import com.bineesh.mooncartsettlement.domain.enums.DiscrepancyType
import com.bineesh.mooncartsettlement.domain.enums.SourceEntityType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "discrepancies")
class Discrepancy(
    @Id
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reconciliation_run_id", nullable = false)
    var reconciliationRun: ReconciliationRun,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_group_id")
    var matchGroup: MatchGroup? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    var type: DiscrepancyType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var severity: DiscrepancySeverity,

    @Enumerated(EnumType.STRING)
    @Column(name = "source_entity_type", nullable = false, length = 32)
    var sourceEntityType: SourceEntityType,

    @Column(name = "source_entity_id", nullable = false)
    var sourceEntityId: UUID,

    @Column(length = 3)
    var currency: String? = null,

    @Column(precision = 19, scale = 4)
    var amount: BigDecimal? = null,

    @Column(name = "merchant_id", length = 64)
    var merchantId: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    var details: Map<String, Any?>? = null,

    @Column(name = "investigation_priority", precision = 10, scale = 4)
    var investigationPriority: BigDecimal? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)
