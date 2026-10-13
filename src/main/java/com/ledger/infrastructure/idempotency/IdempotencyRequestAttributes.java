package com.ledger.infrastructure.idempotency;

import com.ledger.domain.exception.IdempotencyKeyRequiredException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class IdempotencyRequestAttributes {

    public static final String KEY = "com.ledger.idempotency.key";
    public static final String HASH = "com.ledger.idempotency.hash";
    public static final String REPLAYED = "com.ledger.idempotency.replayed";

    private IdempotencyRequestAttributes() {
    }

    public static void requireKey(String presented) {
        if (!key().equals(presented)) {
            throw new IdempotencyKeyRequiredException("Idempotency-Key header did not match the request context");
        }
    }

    public static String key() {
        Object value = current().getAttribute(KEY);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IdempotencyKeyRequiredException("Idempotency-Key header is required");
        }
        return text;
    }

    public static String hash() {
        Object value = current().getAttribute(HASH);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalStateException("Request hash is missing");
        }
        return text;
    }

    public static void markReplayed() {
        current().setAttribute(REPLAYED, Boolean.TRUE);
    }

    public static boolean isReplayed() {
        return Boolean.TRUE.equals(current().getAttribute(REPLAYED));
    }

    private static HttpServletRequest current() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new IllegalStateException("Idempotent posting must run on the HTTP request thread");
        }
        return attributes.getRequest();
    }
}
