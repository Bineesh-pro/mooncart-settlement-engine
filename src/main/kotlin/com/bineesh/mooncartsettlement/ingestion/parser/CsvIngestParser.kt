package com.bineesh.mooncartsettlement.ingestion.parser

import com.bineesh.mooncartsettlement.domain.enums.PaymentStatus
import com.bineesh.mooncartsettlement.domain.enums.YunoTransactionStatus
import com.bineesh.mooncartsettlement.ingestion.dto.BankSettlementIngestDto
import com.bineesh.mooncartsettlement.ingestion.dto.InternalOrderIngestDto
import com.bineesh.mooncartsettlement.ingestion.dto.YunoTransactionIngestDto
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.springframework.stereotype.Component
import java.io.InputStream
import java.io.InputStreamReader
import java.math.BigDecimal

@Component
class CsvIngestParser {
    fun parseYunoTransactions(input: InputStream): List<YunoTransactionIngestDto> =
        parse(input) { record ->
            YunoTransactionIngestDto(
                yunoTransactionId = record.get("yuno_transaction_id"),
                timestamp = record.get("timestamp"),
                amount = BigDecimal(record.get("amount")),
                currency = record.get("currency"),
                status = YunoTransactionStatus.valueOf(record.get("status").uppercase()),
                merchantId = record.get("merchant_id"),
                customerEmail = record.get("customer_email"),
                orderReference = record.get("order_reference"),
            )
        }

    fun parseBankSettlements(input: InputStream): List<BankSettlementIngestDto> =
        parse(input) { record ->
            BankSettlementIngestDto(
                bankReferenceNumber = record.get("bank_reference_number"),
                settlementDate = record.get("settlement_date"),
                settledAmount = BigDecimal(record.get("settled_amount")),
                currency = record.get("currency"),
                yunoTransactionId = record.get("yuno_transaction_id").ifBlank { null },
            )
        }

    fun parseInternalOrders(input: InputStream): List<InternalOrderIngestDto> =
        parse(input) { record ->
            InternalOrderIngestDto(
                orderId = record.get("order_id"),
                customerEmail = record.get("customer_email"),
                orderAmount = BigDecimal(record.get("order_amount")),
                currency = record.get("currency"),
                timestamp = record.get("timestamp"),
                paymentStatus = PaymentStatus.valueOf(record.get("payment_status").uppercase()),
            )
        }

    private fun <T> parse(input: InputStream, mapper: (org.apache.commons.csv.CSVRecord) -> T): List<T> {
        InputStreamReader(input).use { reader ->
            CSVParser.parse(
                reader,
                CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreHeaderCase(true)
                    .setTrim(true)
                    .build(),
            ).use { parser ->
                return parser.records.map(mapper)
            }
        }
    }
}
