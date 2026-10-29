package com.pointbank.bankplatform.dto;

public record AccountTransferResponse(
        String transferReference,
        AccountTransactionResponse debit,
        AccountTransactionResponse credit
) {
}
