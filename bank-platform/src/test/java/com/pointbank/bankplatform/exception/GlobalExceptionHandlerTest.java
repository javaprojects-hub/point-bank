package com.pointbank.bankplatform.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.math.BigDecimal;

import com.pointbank.bankplatform.config.RequestCorrelationFilter;
import com.pointbank.bankplatform.constant.AccountTransactionType;
import com.pointbank.bankplatform.constant.AccountType;
import com.pointbank.bankplatform.dto.DailyTransactionLimitDetails;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    void dailyLimitExceptionMapsTo422WithStructuredDetailsAndCorrelationId() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/accounts/transactions");
        request.setAttribute(RequestCorrelationFilter.REQUEST_ID_ATTRIBUTE, "request-123");
        DailyTransactionLimitExceededException exception = new DailyTransactionLimitExceededException(
                new DailyTransactionLimitDetails(
                        AccountType.SAVINGS,
                        AccountTransactionType.DEPOSIT,
                        new BigDecimal("99999.99"),
                        new BigDecimal("60000.00"),
                        new BigDecimal("40000.00"),
                        new BigDecimal("39999.99"),
                        "INR"
                )
        );

        var response = exceptionHandler.handleBankingBusinessFailure(exception, request);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals("DAILY_TRANSACTION_LIMIT_EXCEEDED", response.getBody().code());
        assertEquals("request-123", response.getBody().requestId());
        assertEquals(AccountType.SAVINGS,
                ((DailyTransactionLimitDetails) response.getBody().details()).accountType());
        assertFalse(response.getBody().message().contains("stack"));
    }

    @Test
    void unexpectedErrorResponseDoesNotExposeExceptionDetails() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/accounts");
        RuntimeException exception = new RuntimeException("password=secret database=internal");

        var response = exceptionHandler.handleUnexpectedException(exception, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", response.getBody().code());
        assertEquals("An unexpected error occurred.", response.getBody().message());
        assertFalse(response.getBody().message().contains("secret"));
        assertFalse(response.getBody().message().contains("internal"));
    }
}
