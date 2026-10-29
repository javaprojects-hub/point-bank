package com.pointbank.bankplatform.dto;

import com.pointbank.bankplatform.constant.AccountType;

import jakarta.validation.constraints.NotNull;

public record AccountOpenRequest(
        @NotNull(message = "account type is required") AccountType accountType
) {
}
