package com.bineesh.mooncartsettlement.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun openApi(): OpenAPI =
        OpenAPI().info(
            Info()
                .title("MoonCart Settlement Matching API")
                .description("Settlement reconciliation and discrepancy investigation API")
                .version("1.0.0"),
        )
}
