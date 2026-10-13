package com.ledger.infrastructure.security;

import com.ledger.infrastructure.config.LedgerSecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    static final String ISSUER = "fintech-ledger-core";

    private final LedgerSecurityProperties properties;
    private final SecretKey key;

    public JwtService(LedgerSecurityProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.jwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String issue(String subject) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(properties.ttlMinutes() * 60);
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    public String subject(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(ISSUER)
                .clockSkewSeconds(30)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    public long expiresInSeconds() {
        return properties.ttlMinutes() * 60;
    }
}
