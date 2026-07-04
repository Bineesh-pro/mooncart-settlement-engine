package com.bineesh.mooncartsettlement.ingestion.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object DateTimeParser {
    private val formatters = listOf(
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,
        DateTimeFormatter.ISO_ZONED_DATE_TIME,
        DateTimeFormatter.ISO_INSTANT,
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
    )

    fun parseInstant(value: String): Instant {
        val trimmed = value.trim()
        formatters.forEach { formatter ->
            try {
                return ZonedDateTime.parse(trimmed, formatter).toInstant()
            } catch (_: DateTimeParseException) {
            }
            try {
                return Instant.parse(trimmed)
            } catch (_: DateTimeParseException) {
            }
        }
        try {
            return java.time.LocalDateTime.parse(trimmed).atZone(ZoneOffset.UTC).toInstant()
        } catch (ex: DateTimeParseException) {
            throw IllegalArgumentException("Unable to parse timestamp: $value", ex)
        }
    }

    fun parseLocalDate(value: String): LocalDate =
        try {
            LocalDate.parse(value.trim())
        } catch (ex: DateTimeParseException) {
            try {
                LocalDate.parse(value.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            } catch (inner: DateTimeParseException) {
                throw IllegalArgumentException("Unable to parse date: $value", inner)
            }
        }
}

object CurrencyValidator {
    private val allowed = setOf("IDR", "PHP", "USD")

    fun validate(currency: String): String {
        val normalized = currency.trim().uppercase()
        require(normalized in allowed) { "Unsupported currency: $currency. Allowed: $allowed" }
        return normalized
    }
}
