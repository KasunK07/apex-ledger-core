package com.ledger.infrastructure.security;

import com.ledger.infrastructure.config.LedgerSecurityProperties;
import com.ledger.api.dto.request.TokenRequest;
import com.ledger.api.dto.response.TokenResponse;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class AuthService {

    private final LedgerSecurityProperties properties;
    private final JwtService jwtService;

    public AuthService(LedgerSecurityProperties properties, JwtService jwtService) {
        this.properties = properties;
        this.jwtService = jwtService;
    }

    public TokenResponse authenticate(TokenRequest request) {
        if (!matches(properties.clientId(), request.clientId()) || !matches(properties.clientSecret(), request.clientSecret())) {
            return null;
        }
        return new TokenResponse(jwtService.issue(properties.clientId()), "Bearer", jwtService.expiresInSeconds());
    }

    private boolean matches(String expected, String actual) {
        return MessageDigest.isEqual(sha256(expected), sha256(actual == null ? "" : actual));
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
