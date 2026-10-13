package com.ledger.api.controller;

import com.ledger.api.dto.request.TokenRequest;
import com.ledger.api.dto.response.TokenResponse;
import com.ledger.api.error.ApiErrorWriter;
import com.ledger.infrastructure.security.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
@SecurityRequirements
public class AuthController {

    private final AuthService authService;
    private final ApiErrorWriter apiErrorWriter;

    public AuthController(AuthService authService, ApiErrorWriter apiErrorWriter) {
        this.authService = authService;
        this.apiErrorWriter = apiErrorWriter;
    }

    @PostMapping("/token")
    @Operation(summary = "Issue a bearer token for the configured API client")
    public ResponseEntity<?> token(@Valid @RequestBody TokenRequest request, HttpServletRequest httpRequest) {
        TokenResponse response = authService.authenticate(request);
        if (response == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(apiErrorWriter.body(
                    HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid client credentials", httpRequest.getRequestURI(), List.of()));
        }
        return ResponseEntity.ok(response);
    }
}
