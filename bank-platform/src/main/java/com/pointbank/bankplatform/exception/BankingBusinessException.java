package com.pointbank.bankplatform.exception;

import org.springframework.http.HttpStatus;
import com.pointbank.bankplatform.dto.ErrorDetails;

public abstract class BankingBusinessException extends RuntimeException {

    private final String code;
    private final HttpStatus httpStatus;
    private final ErrorDetails details;

    protected BankingBusinessException(String code, String message, HttpStatus httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.details = null;
    }

    protected BankingBusinessException(String code, String message, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.httpStatus = httpStatus;
        this.details = null;
    }

    protected BankingBusinessException(
            String code,
            String message,
            HttpStatus httpStatus,
            ErrorDetails details
    ) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.details = details;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public ErrorDetails getDetails() {
        return details;
    }
}
