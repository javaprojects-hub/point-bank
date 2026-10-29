package com.pointbank.bankplatform.dto;

import java.math.BigDecimal;

import com.pointbank.bankplatform.constant.AccountTransactionType;
import com.pointbank.bankplatform.constant.AccountType;

public record DailyTransactionLimitDetails(
        AccountType accountType,
        AccountTransactionType transactionType,
        BigDecimal dailyLimit,
        BigDecimal amountAlreadyCounted,
        BigDecimal requestedAmount,
        BigDecimal remainingLimit,
        String currency
) implements ErrorDetails {
}
