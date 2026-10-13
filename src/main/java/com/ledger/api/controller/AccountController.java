package com.ledger.api.controller;

import com.ledger.infrastructure.idempotency.IdempotencyRequestAttributes;
import com.ledger.infrastructure.idempotency.Idempotent;
import com.ledger.infrastructure.idempotency.IdempotencyKeys;
import com.ledger.api.dto.response.AccountResponse;
import com.ledger.api.dto.response.AccountStatementResponse;
import com.ledger.api.dto.request.CashMovementRequest;
import com.ledger.api.dto.response.CashMovementResponse;
import com.ledger.api.dto.request.OpenAccountRequest;
import com.ledger.application.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts")
@SecurityRequirement(name = "bearer-jwt")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Open a currency account")
    public AccountResponse open(@Valid @RequestBody OpenAccountRequest request) {
        return accountService.open(request);
    }

    @Idempotent
    @PostMapping("/{id}/deposits")
    @Operation(summary = "Deposit funds from the currency treasury into a customer account")
    public ResponseEntity<CashMovementResponse> deposit(
            @PathVariable UUID id,
            @RequestHeader(IdempotencyKeys.HEADER)
            @NotBlank
            @Size(min = 8, max = 200)
            @Pattern(regexp = IdempotencyKeys.PATTERN)
            @Parameter(description = "Unique retry key. The same key and body replay the original response.", required = true)
            String idempotencyKey,
            @Valid @RequestBody CashMovementRequest request) {
        CashMovementResponse response = accountService.deposit(idempotencyKey, id, request);
        return ResponseEntity.status(status()).body(response);
    }

    @Idempotent
    @PostMapping("/{id}/withdrawals")
    @Operation(summary = "Withdraw funds from a customer account back to the currency treasury")
    public ResponseEntity<CashMovementResponse> withdraw(
            @PathVariable UUID id,
            @RequestHeader(IdempotencyKeys.HEADER)
            @NotBlank
            @Size(min = 8, max = 200)
            @Pattern(regexp = IdempotencyKeys.PATTERN)
            @Parameter(description = "Unique retry key. The same key and body replay the original response.", required = true)
            String idempotencyKey,
            @Valid @RequestBody CashMovementRequest request) {
        CashMovementResponse response = accountService.withdraw(idempotencyKey, id, request);
        return ResponseEntity.status(status()).body(response);
    }

    @GetMapping("/{id}/statement")
    @Operation(summary = "Read the account balance and double-entry history")
    public AccountStatementResponse statement(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return accountService.statement(id, page, size);
    }

    private HttpStatus status() {
        return IdempotencyRequestAttributes.isReplayed() ? HttpStatus.OK : HttpStatus.CREATED;
    }
}
