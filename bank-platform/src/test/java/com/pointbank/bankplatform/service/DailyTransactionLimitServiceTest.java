package com.pointbank.bankplatform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.pointbank.bankplatform.config.DailyTransactionLimitProperties;
import com.pointbank.bankplatform.constant.AccountTransactionStatus;
import com.pointbank.bankplatform.constant.AccountTransactionType;
import com.pointbank.bankplatform.constant.AccountType;
import com.pointbank.bankplatform.dto.DailyTransactionLimitDetails;
import com.pointbank.bankplatform.entity.Account;
import com.pointbank.bankplatform.exception.DailyTransactionLimitExceededException;
import com.pointbank.bankplatform.repository.AccountTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DailyTransactionLimitServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    @Mock
    private AccountTransactionRepository transactionRepository;

    private DailyTransactionLimitProperties properties;
    private DailyTransactionLimitService limitService;
    private Account savingsAccount;
    private Account salaryAccount;
    private Account currentAccount;

    @BeforeEach
    void setUp() {
        properties = new DailyTransactionLimitProperties();
        properties.setRollingWindow(Duration.ofHours(24));
        limitService = new DailyTransactionLimitService(transactionRepository, properties);
        savingsAccount = account(AccountType.SAVINGS);
        salaryAccount = account(AccountType.SALARY);
        currentAccount = account(AccountType.CURRENT);
    }

    @Test
    void savingsDepositExactlyAtLimitIsAllowed() {
        givenPostedTotal(savingsAccount, AccountTransactionType.DEPOSIT, ZERO);

        limitService.assertWithinLimit(
                savingsAccount, AccountTransactionType.DEPOSIT, new BigDecimal("99999.99"), NOW
        );
    }

    @Test
    void savingsDepositOverLimitIsRejectedWithSafeStructuredDetails() {
        givenPostedTotal(savingsAccount, AccountTransactionType.DEPOSIT, ZERO);

        DailyTransactionLimitExceededException exception = assertThrows(
                DailyTransactionLimitExceededException.class,
                () -> limitService.assertWithinLimit(
                        savingsAccount,
                        AccountTransactionType.DEPOSIT,
                        new BigDecimal("100000.00"),
                        NOW
                )
        );

        DailyTransactionLimitDetails details = (DailyTransactionLimitDetails) exception.getDetails();
        assertEquals(AccountType.SAVINGS, details.accountType());
        assertEquals(AccountTransactionType.DEPOSIT, details.transactionType());
        assertEquals(new BigDecimal("99999.99"), details.dailyLimit());
        assertEquals(ZERO, details.amountAlreadyCounted());
        assertEquals(new BigDecimal("100000.00"), details.requestedAmount());
        assertEquals(new BigDecimal("99999.99"), details.remainingLimit());
        assertEquals("INR", details.currency());
    }

    @Test
    void cumulativeSavingsDepositsAllowExactBoundaryAndRejectNextPaise() {
        givenPostedTotal(savingsAccount, AccountTransactionType.DEPOSIT, new BigDecimal("60000.00"));
        limitService.assertWithinLimit(
                savingsAccount, AccountTransactionType.DEPOSIT, new BigDecimal("39999.99"), NOW
        );
        when(transactionRepository.sumPostedAmount(
                eq(savingsAccount.getId()),
                eq(AccountTransactionType.DEPOSIT),
                eq(AccountTransactionStatus.COMPLETED),
                eq(NOW.minus(Duration.ofHours(24))),
                eq(NOW)
        )).thenReturn(new BigDecimal("99999.99"));

        assertThrows(
                DailyTransactionLimitExceededException.class,
                () -> limitService.assertWithinLimit(
                        savingsAccount, AccountTransactionType.DEPOSIT, new BigDecimal("0.01"), NOW
                )
        );
    }

    @Test
    void savingsWithdrawalsUseTheirOwnLimitIndependentOfDeposits() {
        givenPostedTotal(savingsAccount, AccountTransactionType.WITHDRAWAL, ZERO);
        limitService.assertWithinLimit(
                savingsAccount, AccountTransactionType.WITHDRAWAL, new BigDecimal("99999.99"), NOW
        );
        verify(transactionRepository).sumPostedAmount(
                eq(savingsAccount.getId()),
                eq(AccountTransactionType.WITHDRAWAL),
                eq(AccountTransactionStatus.COMPLETED),
                eq(NOW.minus(Duration.ofHours(24))),
                eq(NOW)
        );
    }

    @Test
    void transferDirectionsShareExistingDepositAndWithdrawalLimits() {
        givenPostedTotal(savingsAccount, AccountTransactionType.WITHDRAWAL, new BigDecimal("500.00"));
        givenPostedTotal(savingsAccount, AccountTransactionType.TRANSFER_OUT, new BigDecimal("300.00"));
        limitService.assertWithinLimit(
                savingsAccount, AccountTransactionType.TRANSFER_OUT, new BigDecimal("99199.99"), NOW
        );
        DailyTransactionLimitExceededException withdrawalLimit = assertThrows(
                DailyTransactionLimitExceededException.class,
                () -> limitService.assertWithinLimit(
                        savingsAccount, AccountTransactionType.TRANSFER_OUT, new BigDecimal("99200.01"), NOW
                )
        );
        assertEquals(
                AccountTransactionType.TRANSFER_OUT,
                ((DailyTransactionLimitDetails) withdrawalLimit.getDetails()).transactionType()
        );

        givenPostedTotal(savingsAccount, AccountTransactionType.DEPOSIT, new BigDecimal("200.00"));
        givenPostedTotal(savingsAccount, AccountTransactionType.TRANSFER_IN, new BigDecimal("100.00"));
        limitService.assertWithinLimit(
                savingsAccount, AccountTransactionType.TRANSFER_IN, new BigDecimal("99699.99"), NOW
        );
    }

    @Test
    void salaryDepositExactlyAtLimitIsAllowedAndOverLimitIsRejected() {
        givenPostedTotal(salaryAccount, AccountTransactionType.DEPOSIT, ZERO);
        limitService.assertWithinLimit(
                salaryAccount, AccountTransactionType.DEPOSIT, new BigDecimal("300000.00"), NOW
        );
        assertThrows(
                DailyTransactionLimitExceededException.class,
                () -> limitService.assertWithinLimit(
                        salaryAccount, AccountTransactionType.DEPOSIT, new BigDecimal("300000.01"), NOW
                )
        );
    }

    @Test
    void salaryWithdrawalsUseTheirOwnThreeHundredThousandLimit() {
        givenPostedTotal(salaryAccount, AccountTransactionType.WITHDRAWAL, ZERO);

        limitService.assertWithinLimit(
                salaryAccount, AccountTransactionType.WITHDRAWAL, new BigDecimal("300000.00"), NOW
        );
    }

    @Test
    void rollingWindowStartsExactlyTwentyFourHoursBeforeNowAndCountsOnlyCompletedRows() {
        givenPostedTotal(savingsAccount, AccountTransactionType.DEPOSIT, new BigDecimal("50000.00"));

        limitService.assertWithinLimit(
                savingsAccount, AccountTransactionType.DEPOSIT, new BigDecimal("49999.99"), NOW
        );

        verify(transactionRepository).sumPostedAmount(
                eq(savingsAccount.getId()),
                eq(AccountTransactionType.DEPOSIT),
                eq(AccountTransactionStatus.COMPLETED),
                eq(NOW.minus(Duration.ofHours(24))),
                eq(NOW)
        );
    }

    @Test
    void currentAccountIsUnrestrictedByDefaultAndSupportsConfiguredDirectionLimits() {
        limitService.assertWithinLimit(
                currentAccount, AccountTransactionType.DEPOSIT, new BigDecimal("999999999.99"), NOW
        );
        verify(transactionRepository, never()).sumPostedAmount(
                eq(currentAccount.getId()),
                eq(AccountTransactionType.DEPOSIT),
                eq(AccountTransactionStatus.COMPLETED),
                eq(NOW.minus(Duration.ofHours(24))),
                eq(NOW)
        );

        properties.getCurrent().setDeposits(new BigDecimal("5000.00"));
        givenPostedTotal(currentAccount, AccountTransactionType.DEPOSIT, ZERO);
        assertThrows(
                DailyTransactionLimitExceededException.class,
                () -> limitService.assertWithinLimit(
                        currentAccount, AccountTransactionType.DEPOSIT, new BigDecimal("5000.01"), NOW
                )
        );
    }

    private void givenPostedTotal(Account account, AccountTransactionType type, BigDecimal total) {
        when(transactionRepository.sumPostedAmount(
                eq(account.getId()),
                eq(type),
                eq(AccountTransactionStatus.COMPLETED),
                eq(NOW.minus(Duration.ofHours(24))),
                eq(NOW)
        )).thenReturn(total);
    }

    private Account account(AccountType type) {
        Account account = new Account();
        account.setId(UUID.randomUUID());
        account.setAccountType(type);
        account.setCurrencyCode("INR");
        return account;
    }
}
