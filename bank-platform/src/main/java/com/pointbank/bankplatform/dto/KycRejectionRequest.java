package com.pointbank.bankplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KycRejectionRequest(
        @NotBlank(message = "A rejection reason is required.")
        @Size(max = 500, message = "The rejection reason cannot exceed 500 characters.")
        String reason
) {
}
