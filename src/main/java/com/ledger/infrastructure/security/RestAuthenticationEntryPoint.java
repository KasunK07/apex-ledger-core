package com.ledger.infrastructure.security;

import com.ledger.api.error.ApiErrorWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ApiErrorWriter apiErrorWriter;

    public RestAuthenticationEntryPoint(ApiErrorWriter apiErrorWriter) {
        this.apiErrorWriter = apiErrorWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        apiErrorWriter.write(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required", request.getRequestURI());
    }
}
