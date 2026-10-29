package com.pointbank.bankplatform.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.pointbank.bankplatform.constant.AccountStatus;
import com.pointbank.bankplatform.constant.AccountType;

public record AccountResponse(
        Long accountId,
        String accountNumber,
        AccountType accountType,
        AccountStatus accountStatus,
        String currencyCode,
        BigDecimal availableBalance,
        BigDecimal ledgerBalance,
        Instant createdAt,
        Instant activatedAt,
        Instant closedAt
) {
}
