package com.pointbank.bankplatform.exception;

import com.pointbank.bankplatform.constant.AccountStatus;
import org.springframework.http.HttpStatus;

public class AccountNotActiveException extends BankingBusinessException {

    public AccountNotActiveException(AccountStatus status) {
        super(
                "ACCOUNT_NOT_ACTIVE",
                "The account is not eligible for this transaction while its status is " + status + ".",
                HttpStatus.CONFLICT
        );
    }
}
