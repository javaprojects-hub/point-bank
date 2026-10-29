package com.pointbank.bankplatform.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import com.pointbank.bankplatform.constant.AccountTransactionStatus;
import com.pointbank.bankplatform.constant.AccountStatus;
import com.pointbank.bankplatform.constant.AccountTransactionStatus;
import com.pointbank.bankplatform.constant.AccountTransactionType;
import com.pointbank.bankplatform.constant.AccountType;
import com.pointbank.bankplatform.dto.AccountResponse;
import com.pointbank.bankplatform.dto.AccountTransactionResponse;
import com.pointbank.bankplatform.service.AccountService;
import com.pointbank.bankplatform.service.CustomerIdentityService;
import com.pointbank.bankplatform.service.TransactionStatementPdfService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AccountControllerTest {

    private AccountService accountService;
    private CustomerIdentityService customerIdentityService;
    private TransactionStatementPdfService transactionStatementPdfService;
    private MockMvc mockMvc;
    private JwtAuthenticationToken customerAuthentication;

    @BeforeEach
    void setUp() {
        accountService = mock(AccountService.class);
        customerIdentityService = mock(CustomerIdentityService.class);
        transactionStatementPdfService = mock(TransactionStatementPdfService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AccountController(accountService, customerIdentityService, transactionStatementPdfService)
        ).build();
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("customer-subject")
                .claim("iss", "https://issuer.example")
                .build();
        customerAuthentication = new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")),
                "customer-subject"
        );
    }

    @Test
    void customerCanOpenAccountUsingAuthenticatedIdentity() throws Exception {
        AccountResponse account = new AccountResponse(
                10_000_001L,
                "12345678901234",
                AccountType.SAVINGS,
                AccountStatus.PENDING,
                "INR",
                BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2),
                null,
                null,
                null
        );
        when(customerIdentityService.getAuthenticatedCustomerId(any(Jwt.class))).thenReturn(123L);
        when(accountService.openAccount(123L, AccountType.SAVINGS)).thenReturn(account);

        mockMvc.perform(post("/api/v1/accounts")
                        .principal(customerAuthentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\":\"SAVINGS\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/accounts/12345678901234"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accountId").value(10000001))
                .andExpect(jsonPath("$.data.accountNumber").value("12345678901234"))
                .andExpect(jsonPath("$.data.accountStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.id").doesNotExist());
        verify(accountService).openAccount(123L, AccountType.SAVINGS);
    }

    @Test
    void customerDepositUsesIdentityAndIdempotencyKey() throws Exception {
        AccountTransactionResponse transaction = new AccountTransactionResponse(
                "transaction-reference",
                "12345678901234",
                AccountTransactionType.DEPOSIT,
                AccountTransactionStatus.COMPLETED,
                new BigDecimal("25.00"),
                "INR",
                new BigDecimal("125.00"),
                new BigDecimal("125.00"),
                "SIMULATED",
                null,
                java.time.Instant.parse("2026-10-29T00:00:00Z"),
                null,
                null
        );
        when(customerIdentityService.getAuthenticatedCustomerId(any(Jwt.class))).thenReturn(123L);
        when(accountService.deposit(
                eq("12345678901234"),
                eq(123L),
                eq("request-123"),
                eq("customer-subject"),
                eq(new BigDecimal("25.00"))
        )).thenReturn(transaction);

        mockMvc.perform(post("/api/v1/accounts/12345678901234/deposits")
                        .principal(customerAuthentication)
                        .header("Idempotency-Key", "request-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":25.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.channel").value("SIMULATED"))
                .andExpect(jsonPath("$.data.availableBalanceAfter").value(125.0));

        verify(accountService).deposit(
                "12345678901234",
                123L,
                "request-123",
                "customer-subject",
                new BigDecimal("25.00")
        );
    }

    @Test
    void customerTransferUsesAuthenticatedIdentityAndIdempotencyKey() throws Exception {
        AccountTransactionResponse debit = new AccountTransactionResponse(
                "debit-reference",
                "12345678901234",
                AccountTransactionType.TRANSFER_OUT,
                AccountTransactionStatus.COMPLETED,
                new BigDecimal("25.00"),
                "INR",
                new BigDecimal("75.00"),
                new BigDecimal("75.00"),
                "INTERNAL",
                null,
                java.time.Instant.parse("2026-10-29T00:00:00Z"),
                "transfer-reference",
                "23456789012345"
        );
        AccountTransactionResponse credit = new AccountTransactionResponse(
                "credit-reference",
                "23456789012345",
                AccountTransactionType.TRANSFER_IN,
                AccountTransactionStatus.COMPLETED,
                new BigDecimal("25.00"),
                "INR",
                new BigDecimal("125.00"),
                new BigDecimal("125.00"),
                "INTERNAL",
                null,
                java.time.Instant.parse("2026-10-29T00:00:00Z"),
                "transfer-reference",
                "12345678901234"
        );
        when(customerIdentityService.getAuthenticatedCustomerId(any(Jwt.class))).thenReturn(123L);
        when(accountService.transfer(
                eq("12345678901234"),
                eq("23456789012345"),
                eq(123L),
                eq("transfer-request-123"),
                eq("customer-subject"),
                eq(new BigDecimal("25.00"))
        )).thenReturn(new com.pointbank.bankplatform.dto.AccountTransferResponse(
                "transfer-reference", debit, credit
        ));

        mockMvc.perform(post("/api/v1/accounts/12345678901234/transfers")
                        .principal(customerAuthentication)
                        .header("Idempotency-Key", "transfer-request-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destinationAccountNumber\":\"23456789012345\",\"amount\":25.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.transferReference").value("transfer-reference"))
                .andExpect(jsonPath("$.data.debit.transactionType").value("TRANSFER_OUT"))
                .andExpect(jsonPath("$.data.credit.transactionType").value("TRANSFER_IN"));

        verify(accountService).transfer(
                "12345678901234",
                "23456789012345",
                123L,
                "transfer-request-123",
                "customer-subject",
                new BigDecimal("25.00")
        );
    }

    @Test
    void customerCanDownloadMonthlyPdfStatementWithFilters() throws Exception {
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
        when(customerIdentityService.getAuthenticatedCustomerId(any(Jwt.class))).thenReturn(123L);
        when(accountService.getAccount("12345678901234", 123L)).thenReturn(account);
        when(accountService.getStatementTransactions(
                eq("12345678901234"),
                eq(123L),
                eq(java.time.Instant.parse("2026-03-01T00:00:00Z")),
                eq(java.time.Instant.parse("2026-06-01T00:00:00Z")),
                eq(AccountType.SAVINGS),
                eq(AccountTransactionStatus.COMPLETED)
        )).thenReturn(List.of());
        when(transactionStatementPdfService.generate(
                eq(account),
                eq(2026),
                eq(3),
                eq(5),
                eq(AccountType.SAVINGS),
                eq(AccountTransactionStatus.COMPLETED),
                any()
        )).thenReturn("%PDF-test".getBytes(java.nio.charset.StandardCharsets.US_ASCII));

        mockMvc.perform(get("/api/v1/accounts/12345678901234/transactions/statement.pdf")
                        .principal(customerAuthentication)
                        .param("year", "2026")
                        .param("fromMonth", "3")
                        .param("toMonth", "5")
                        .param("accountType", "SAVINGS")
                        .param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string(
                        "Content-Disposition",
                        "attachment; filename=\"transaction-history-12345678901234-2026-03-to-05.pdf\""
                ))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content -> org.junit.jupiter.api.Assertions.assertTrue(
                        content.getResponse().getContentAsByteArray().length > 0
                ));
    }
}
