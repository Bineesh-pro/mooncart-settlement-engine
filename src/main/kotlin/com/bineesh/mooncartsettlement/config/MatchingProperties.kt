package com.bineesh.mooncartsettlement.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "settlement.matching")
data class MatchingProperties(
    val amountTolerancePct: Double = 0.05,
    val amountDiscrepancyThresholdPct: Double = 0.02,
    val amountAbsoluteTolerance: Map<String, Double> = mapOf(
        "USD" to 0.01,
        "PHP" to 0.01,
        "IDR" to 100.0,
    ),
    val settlementDelayMaxDays: Int = 5,
    val timingAnomalyThresholdDays: Int = 3,
    val fuzzyMatchMinScore: Int = 70,
    val maxCandidatesPerRecord: Int = 20,
    val orphanLinkMaxHours: Long = 24,
)
