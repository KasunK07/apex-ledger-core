package com.ledger.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TokenRequest(
        @NotBlank @Size(max = 120) String clientId,
        @NotBlank @Size(max = 200) String clientSecret
) {
}
