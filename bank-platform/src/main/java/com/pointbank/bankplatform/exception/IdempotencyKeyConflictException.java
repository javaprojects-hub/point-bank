package com.pointbank.bankplatform.exception;

import org.springframework.http.HttpStatus;

public class IdempotencyKeyConflictException extends BankingBusinessException {

    public IdempotencyKeyConflictException() {
        super(
                "IDEMPOTENCY_KEY_CONFLICT",
                "Idempotency key was already used with a different transaction request.",
                HttpStatus.CONFLICT
        );
    }
}
