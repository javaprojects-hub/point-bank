package com.pointbank.bankplatform.exception;

import org.springframework.http.HttpStatus;

public class InvalidTransactionAmountException extends BankingBusinessException {

    public InvalidTransactionAmountException(String message) {
        super("INVALID_TRANSACTION_AMOUNT", message, HttpStatus.BAD_REQUEST);
    }
}
