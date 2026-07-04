package com.bineesh.mooncartsettlement.domain.model

import com.bineesh.mooncartsettlement.domain.enums.ReconciliationRunStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "reconciliation_runs")
class ReconciliationRun(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "period_start", nullable = false)
    var periodStart: LocalDate,

    @Column(name = "period_end", nullable = false)
    var periodEnd: LocalDate,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: ReconciliationRunStatus = ReconciliationRunStatus.CREATED,

    @Column(name = "started_at")
    var startedAt: Instant? = null,

    @Column(name = "completed_at")
    var completedAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)
