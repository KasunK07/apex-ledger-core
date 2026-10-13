package com.ledger.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI ledgerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Fintech Ledger Core API")
                        .version("v1")
                        .description("Double-entry ledger. Transfers, deposits, and withdrawals post one journal and exactly two entries."))
                .components(new Components().addSecuritySchemes("bearer-jwt", new SecurityScheme()
                        .name("bearer-jwt")
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }
}
