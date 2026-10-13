package com.ledger.infrastructure.idempotency;

import jakarta.servlet.http.HttpServletRequest;

public final class IdempotentRoutes {

    private IdempotentRoutes() {
    }

    public static boolean matches(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return "/api/v1/transfers".equals(path)
                || path.matches("/api/v1/accounts/[^/]+/deposits")
                || path.matches("/api/v1/accounts/[^/]+/withdrawals");
    }
}
