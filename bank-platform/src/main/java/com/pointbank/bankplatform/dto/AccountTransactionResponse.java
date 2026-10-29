package com.pointbank.bankplatform.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.pointbank.bankplatform.constant.AccountTransactionStatus;
import com.pointbank.bankplatform.constant.AccountTransactionType;

public record AccountTransactionResponse(
        String transactionReference,
        String accountNumber,
        AccountTransactionType transactionType,
        AccountTransactionStatus status,
        BigDecimal amount,
        String currencyCode,
        BigDecimal availableBalanceAfter,
        BigDecimal ledgerBalanceAfter,
        String channel,
        String failureReason,
        Instant createdAt,
        String transferReference,
        String counterpartyAccountNumber
) {
}
