package com.ledger.api.controller;

import com.ledger.infrastructure.idempotency.IdempotencyKeys;
import com.ledger.infrastructure.idempotency.IdempotencyRequestAttributes;
import com.ledger.infrastructure.idempotency.Idempotent;
import com.ledger.api.dto.request.TransferRequest;
import com.ledger.api.dto.response.TransferResponse;
import com.ledger.application.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/transfers")
@Tag(name = "Transfers")
@SecurityRequirement(name = "bearer-jwt")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @Idempotent
    @PostMapping
    @Operation(summary = "Transfer money between two customer accounts",
            description = "Posts one TRANSFER journal with a DEBIT on the source and a CREDIT on the destination. Requires Idempotency-Key.")
    public ResponseEntity<TransferResponse> transfer(
            @RequestHeader(IdempotencyKeys.HEADER)
            @NotBlank
            @Size(min = 8, max = 200)
            @Pattern(regexp = IdempotencyKeys.PATTERN)
            @Parameter(description = "Unique retry key. The same key and body replay the original response.", required = true)
            String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        TransferResponse response = transferService.transfer(idempotencyKey, request);
        HttpStatus status = IdempotencyRequestAttributes.isReplayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }
}
