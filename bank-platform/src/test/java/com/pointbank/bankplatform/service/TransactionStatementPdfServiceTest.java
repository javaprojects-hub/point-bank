package com.pointbank.bankplatform.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.pointbank.bankplatform.constant.AccountStatus;
import com.pointbank.bankplatform.constant.AccountTransactionStatus;
import com.pointbank.bankplatform.constant.AccountTransactionType;
import com.pointbank.bankplatform.constant.AccountType;
import com.pointbank.bankplatform.dto.AccountResponse;
import com.pointbank.bankplatform.dto.AccountTransactionResponse;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class TransactionStatementPdfServiceTest {

    private final TransactionStatementPdfService pdfService = new TransactionStatementPdfService();

    @Test
    void createsReadablePdfWithPeriodAccountAndTransactionDetails() throws Exception {
        AccountResponse account = new AccountResponse(
                10_000_001L,
                "12345678901234",
                AccountType.SAVINGS,
                AccountStatus.ACTIVE,
                "INR",
                new BigDecimal("125.00"),
                new BigDecimal("125.00"),
                null,
                null,
                null
        );
        AccountTransactionResponse transaction = new AccountTransactionResponse(
                "reference-123",
                "12345678901234",
                AccountTransactionType.DEPOSIT,
                AccountTransactionStatus.COMPLETED,
                new BigDecimal("25.00"),
                "INR",
                new BigDecimal("125.00"),
                new BigDecimal("125.00"),
                "SIMULATED",
                null,
                Instant.parse("2026-03-15T10:30:00Z"),
                null,
                null
        );

        byte[] pdf = pdfService.generate(
                account,
                2026,
                3,
                5,
                AccountType.SAVINGS,
                AccountTransactionStatus.COMPLETED,
                List.of(transaction)
        );

        try (var document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("POINTBANK - ACCOUNT TRANSACTION STATEMENT"));
            assertTrue(text.contains("12345678901234"));
            assertTrue(text.contains("2026-03 to 2026-05"));
            assertTrue(text.contains("COMPLETED"));
            assertTrue(text.contains("reference-123"));
            assertTrue(text.contains("25.00"));
        }
    }
}
