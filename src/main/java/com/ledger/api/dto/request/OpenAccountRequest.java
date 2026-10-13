package com.ledger.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

@Schema(description = "Open a zero-balance customer account in a treasury-backed currency.")
public record OpenAccountRequest(
        @NotNull UUID customerId,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency
) {
}
