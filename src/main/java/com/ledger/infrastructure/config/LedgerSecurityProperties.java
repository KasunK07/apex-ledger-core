package com.ledger.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ledger.security")
public record LedgerSecurityProperties(
        String jwtSecret,
        long ttlMinutes,
        String clientId,
        String clientSecret
) {
}
