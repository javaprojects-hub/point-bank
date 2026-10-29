package com.pointbank.bankplatform.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record AccountTransferRequest(
        @NotBlank(message = "destination account number is required")
        @Pattern(regexp = "^[0-9]{14}$", message = "destination account number must contain exactly 14 digits")
        String destinationAccountNumber,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be greater than zero")
        @Digits(integer = 17, fraction = 2, message = "amount must have at most 17 integer digits and 2 decimal places")
        BigDecimal amount
) {
}
