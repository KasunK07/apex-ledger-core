package com.ledger.api.dto.request;

import com.ledger.domain.model.CustomerStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Register a customer. Status defaults to ACTIVE when omitted.")
public record CreateCustomerRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 200) String fullName,
        CustomerStatus status
) {
}
