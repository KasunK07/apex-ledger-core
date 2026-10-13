package com.ledger.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Transfer value between two customer accounts of the same currency.")
public record TransferRequest(
        @NotNull UUID sourceAccountId,
        @NotNull UUID destinationAccountId,
        @NotNull
        @DecimalMin(value = "0.0001")
        @Digits(integer = 15, fraction = 4)
        @Schema(example = "25.0000")
        BigDecimal amount,
        @Size(max = 280) String memo
) {
}
