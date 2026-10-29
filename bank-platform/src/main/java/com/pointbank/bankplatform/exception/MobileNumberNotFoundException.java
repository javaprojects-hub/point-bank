package com.pointbank.bankplatform.exception;

import org.springframework.http.HttpStatus;

public class MobileNumberNotFoundException extends BankingBusinessException {

    public MobileNumberNotFoundException(String message) {

       super ("MOBILE_NUMBER_INVALID", message,  HttpStatus.NOT_FOUND);
    }

}
