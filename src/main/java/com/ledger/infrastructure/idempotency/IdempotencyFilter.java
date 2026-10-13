package com.ledger.infrastructure.idempotency;

import com.ledger.api.error.ApiErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.nio.charset.StandardCharsets;

@Component
public class IdempotencyFilter extends OncePerRequestFilter {

    static final int MAX_BODY_BYTES = 64 * 1024;

    private final ApiErrorWriter apiErrorWriter;

    public IdempotencyFilter(ApiErrorWriter apiErrorWriter) {
        this.apiErrorWriter = apiErrorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !IdempotentRoutes.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        byte[] body = request.getInputStream().readAllBytes();
        if (body.length > MAX_BODY_BYTES) {
            apiErrorWriter.write(response, HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE",
                    "Request body exceeds 64KiB", request.getRequestURI());
            return;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(request, body);
        wrapped.setAttribute(IdempotencyRequestAttributes.HASH, sha256(request.getMethod(), path, body));
        String key = request.getHeader(IdempotencyKeys.HEADER);
        if (key != null) {
            wrapped.setAttribute(IdempotencyRequestAttributes.KEY, key);
        }
        filterChain.doFilter(wrapped, response);
    }

    private String sha256(String method, String path, byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(method.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
            digest.update(path.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
            digest.update(body);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
