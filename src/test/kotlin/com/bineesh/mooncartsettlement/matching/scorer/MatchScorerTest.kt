package com.bineesh.mooncartsettlement.matching.scorer

import com.bineesh.mooncartsettlement.config.MatchingProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class MatchScorerTest {
    private val properties = MatchingProperties()
    private val scorer = MatchScorer(properties)

    @Test
    fun `amount within 5 percent matches`() {
        val result = scorer.compareAmounts(BigDecimal("100.00"), BigDecimal("97.00"), "USD")
        assertTrue(result.withinMatchTolerance)
    }

    @Test
    fun `amount beyond 5 percent does not match`() {
        val result = scorer.compareAmounts(BigDecimal("100.00"), BigDecimal("90.00"), "USD")
        assertFalse(result.withinMatchTolerance)
    }

    @Test
    fun `discrepancy threshold is stricter than match tolerance`() {
        val result = scorer.compareAmounts(BigDecimal("100.00"), BigDecimal("97.00"), "USD")
        assertTrue(result.withinMatchTolerance)
        assertFalse(scorer.isWithinDiscrepancyThreshold(result.variancePct))
    }

    @Test
    fun `IDR absolute tolerance applies`() {
        val result = scorer.compareAmounts(BigDecimal("100000"), BigDecimal("99950"), "IDR")
        assertTrue(result.withinMatchTolerance)
        assertEquals(BigDecimal("0.000500"), result.variancePct)
    }
}
