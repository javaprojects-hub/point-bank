package com.pointbank.bankplatform.exception;

import com.pointbank.bankplatform.dto.DailyTransactionLimitDetails;
import org.springframework.http.HttpStatus;

public class DailyTransactionLimitExceededException extends BankingBusinessException {

    public DailyTransactionLimitExceededException(DailyTransactionLimitDetails details) {
        super(
                "DAILY_TRANSACTION_LIMIT_EXCEEDED",
                "The transaction exceeds the daily limit for this account.",
                HttpStatus.UNPROCESSABLE_ENTITY,
                details
        );
    }
}
