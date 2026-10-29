package com.pointbank.bankplatform.exception;

import org.springframework.http.HttpStatus;

public class InsufficientBalanceException extends BankingBusinessException {

    public InsufficientBalanceException() {
        super(
                "INSUFFICIENT_BALANCE",
                "The account does not have enough available funds for this withdrawal.",
                HttpStatus.UNPROCESSABLE_ENTITY
        );
    }
}
