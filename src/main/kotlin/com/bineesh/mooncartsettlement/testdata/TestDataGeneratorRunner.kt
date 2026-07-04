package com.bineesh.mooncartsettlement.testdata

import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.io.File

@Component
@ConditionalOnProperty(name = ["settlement.generate-test-data"], havingValue = "true")
class TestDataGeneratorRunner(
    private val testDataGenerator: TestDataGenerator,
) : CommandLineRunner {
    override fun run(vararg args: String) {
        val outputDir = System.getenv("SETTLEMENT_TEST_DATA_DIR")
            ?.let { File(it) }
            ?: File("Dev")
        val files = testDataGenerator.generate(outputDir)
        println("Generated test data in ${outputDir.absolutePath}")
        println("Yuno: ${files.yunoFile.name} (${files.transactionCount} rows)")
        println("Bank: ${files.bankFile.name}")
        println("Orders: ${files.orderFile.name}")
    }
}
