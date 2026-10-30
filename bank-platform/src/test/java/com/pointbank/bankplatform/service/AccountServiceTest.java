package com.pointbank.bankplatform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import com.pointbank.bankplatform.constant.AccountStatus;
import com.pointbank.bankplatform.constant.AccountTransactionStatus;
import com.pointbank.bankplatform.constant.AccountTransactionType;
import com.pointbank.bankplatform.constant.AccountType;
import com.pointbank.bankplatform.constant.CustomerStatus;
import com.pointbank.bankplatform.constant.KycStatus;
import com.pointbank.bankplatform.entity.Account;
import com.pointbank.bankplatform.entity.AccountTransaction;
import com.pointbank.bankplatform.entity.Customer;
import com.pointbank.bankplatform.exception.InvalidRequestException;
import com.pointbank.bankplatform.exception.ResourceConflictException;
import com.pointbank.bankplatform.repository.AccountRepository;
import com.pointbank.bankplatform.repository.AccountTransactionRepository;
import com.pointbank.bankplatform.repository.CustomerRepository;
import com.pointbank.bankplatform.utility.AccountIdentifierGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountTransactionRepository transactionRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private AccountIdentifierGenerator identifierGenerator;

    @Mock
    private DailyTransactionLimitService dailyTransactionLimitService;

    @Mock
    private OutboxService outboxService;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountServiceImpl(
                accountRepository,
                transactionRepository,
                customerRepository,
                identifierGenerator,
                dailyTransactionLimitService,
                outboxService,
                java.time.Clock.fixed(
                        java.time.Instant.parse("2026-10-29T00:00:00Z"),
                        java.time.ZoneOffset.UTC
                )
        );
    }

    @Test
    void openAccountRequiresVerifiedKyc() {
        Customer customer = customer(KycStatus.IN_PROGRESS);
        when(customerRepository.findByCustomerIdForUpdate(123L)).thenReturn(Optional.of(customer));

        assertThrows(
                ResourceConflictException.class,
                () -> accountService.openAccount(123L, AccountType.SAVINGS)
        );

        verify(accountRepository, never()).saveAndFlush(any(Account.class));
    }

    @Test
    void openAccountCreatesPendingInrAccountForEligibleCustomer() {
        Customer customer = customer(KycStatus.VERIFIED);
        when(customerRepository.findByCustomerIdForUpdate(123L)).thenReturn(Optional.of(customer));
        when(accountRepository.existsByCustomer_CustomerIdAndAccountType(123L, AccountType.SAVINGS))
                .thenReturn(false);
        when(identifierGenerator.generate()).thenReturn(
                new AccountIdentifierGenerator.GeneratedAccountIdentifiers("12345678901234", 10_000_001L)
        );
        when(accountRepository.saveAndFlush(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = accountService.openAccount(123L, AccountType.SAVINGS);

        assertEquals("12345678901234", response.accountNumber());
        assertEquals(10_000_001L, response.accountId());
        assertEquals(AccountStatus.PENDING, response.accountStatus());
        assertEquals("INR", response.currencyCode());
        assertEquals(BigDecimal.ZERO.setScale(2), response.availableBalance());
        verify(accountRepository).saveAndFlush(any(Account.class));
    }

    @Test
    void openAccountRejectsDuplicateCustomerAccountType() {
        when(customerRepository.findByCustomerIdForUpdate(123L))
                .thenReturn(Optional.of(customer(KycStatus.VERIFIED)));
        when(accountRepository.existsByCustomer_CustomerIdAndAccountType(123L, AccountType.CURRENT))
                .thenReturn(true);

        assertThrows(
                ResourceConflictException.class,
                () -> accountService.openAccount(123L, AccountType.CURRENT)
        );

        verify(identifierGenerator, never()).generate();
    }

    @Test
    void depositUpdatesBothBalancesAndPersistsCompletedSimulatedTransaction() {
        Account account = activeAccount();
        when(accountRepository.findByAccountNumberForUpdate(account.getAccountNumber()))
                .thenReturn(Optional.of(account));
        when(transactionRepository.findByAccount_IdAndIdempotencyKey(account.getId(), "deposit-1"))
                .thenReturn(Optional.empty());
        when(transactionRepository.saveAndFlush(any(AccountTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = accountService.deposit(
                account.getAccountNumber(), 123L, "deposit-1", "customer-subject", new BigDecimal("25.50")
        );

        assertEquals(AccountTransactionStatus.COMPLETED, response.status());
        assertEquals(new BigDecimal("125.50"), account.getAvailableBalance());
        assertEquals(new BigDecimal("125.50"), account.getLedgerBalance());
        assertEquals(new BigDecimal("125.50"), response.availableBalanceAfter());
        verify(transactionRepository).saveAndFlush(any(AccountTransaction.class));
        verify(dailyTransactionLimitService).assertWithinLimit(
                account,
                AccountTransactionType.DEPOSIT,
                new BigDecimal("25.50"),
                java.time.Instant.parse("2026-10-29T00:00:00Z")
        );
    }

    @Test
    void withdrawalWithInsufficientFundsIsRecordedAsFailedWithoutChangingBalance() {
        Account account = activeAccount();
        when(accountRepository.findByAccountNumberForUpdate(account.getAccountNumber()))
                .thenReturn(Optional.of(account));
        when(transactionRepository.findByAccount_IdAndIdempotencyKey(account.getId(), "withdraw-1"))
                .thenReturn(Optional.empty());
        when(transactionRepository.saveAndFlush(any(AccountTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = accountService.withdraw(
                account.getAccountNumber(), 123L, "withdraw-1", "customer-subject", new BigDecimal("101.00")
        );

        assertEquals(AccountTransactionStatus.FAILED, response.status());
        assertEquals("INSUFFICIENT_BALANCE", response.failureReason());
        assertEquals(new BigDecimal("100.00"), account.getAvailableBalance());
        verify(transactionRepository).saveAndFlush(any(AccountTransaction.class));
    }

    @Test
    void transferAtomicallyPostsDebitAndCreditAndLocksAccountsInNumberOrder() {
        Account source = activeAccount();
        Account destination = activeAccount();
        destination.setAccountNumber("23456789012345");
        destination.getCustomer().setCustomerId(456L);
        destination.setAvailableBalance(new BigDecimal("50.00"));
        destination.setLedgerBalance(new BigDecimal("50.00"));
        when(accountRepository.findByAccountNumberForUpdate(source.getAccountNumber()))
                .thenReturn(Optional.of(source));
        when(accountRepository.findByAccountNumberForUpdate(destination.getAccountNumber()))
                .thenReturn(Optional.of(destination));
        when(transactionRepository.findByAccount_IdAndIdempotencyKey(source.getId(), "transfer-1"))
                .thenReturn(Optional.empty());

        var response = accountService.transfer(
                source.getAccountNumber(),
                destination.getAccountNumber(),
                123L,
                "transfer-1",
                "customer-subject",
                new BigDecimal("25.00")
        );

        assertEquals(AccountTransactionStatus.COMPLETED, response.debit().status());
        assertEquals(AccountTransactionStatus.COMPLETED, response.credit().status());
        assertEquals(AccountTransactionType.TRANSFER_OUT, response.debit().transactionType());
        assertEquals(AccountTransactionType.TRANSFER_IN, response.credit().transactionType());
        assertEquals(response.transferReference(), response.debit().transferReference());
        assertEquals(response.transferReference(), response.credit().transferReference());
        assertEquals(new BigDecimal("75.00"), source.getAvailableBalance());
        assertEquals(new BigDecimal("75.00"), source.getLedgerBalance());
        assertEquals(new BigDecimal("75.00"), destination.getAvailableBalance());
        assertEquals(new BigDecimal("75.00"), destination.getLedgerBalance());
        verify(transactionRepository).saveAllAndFlush(any());
        verify(outboxService).recordMoneyTransferred(
                response.transferReference(),
                source.getAccountNumber(),
                destination.getAccountNumber(),
                new BigDecimal("25.00"),
                "INR",
                java.time.Instant.parse("2026-10-29T00:00:00Z")
        );
        verify(dailyTransactionLimitService).assertWithinLimit(
                source,
                AccountTransactionType.TRANSFER_OUT,
                new BigDecimal("25.00"),
                java.time.Instant.parse("2026-10-29T00:00:00Z")
        );
        verify(dailyTransactionLimitService).assertWithinLimit(
                destination,
                AccountTransactionType.TRANSFER_IN,
                new BigDecimal("25.00"),
                java.time.Instant.parse("2026-10-29T00:00:00Z")
        );
        var lockOrder = inOrder(accountRepository);
        lockOrder.verify(accountRepository).findByAccountNumberForUpdate("12345678901234");
        lockOrder.verify(accountRepository).findByAccountNumberForUpdate("23456789012345");
    }

    @Test
    void transferWithInsufficientFundsRecordsFailureWithoutChangingEitherBalance() {
        Account source = activeAccount();
        Account destination = activeAccount();
        destination.setAccountNumber("23456789012345");
        when(accountRepository.findByAccountNumberForUpdate(source.getAccountNumber()))
                .thenReturn(Optional.of(source));
        when(accountRepository.findByAccountNumberForUpdate(destination.getAccountNumber()))
                .thenReturn(Optional.of(destination));
        when(transactionRepository.findByAccount_IdAndIdempotencyKey(source.getId(), "transfer-insufficient"))
                .thenReturn(Optional.empty());
        when(transactionRepository.saveAndFlush(any(AccountTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = accountService.transfer(
                source.getAccountNumber(),
                destination.getAccountNumber(),
                123L,
                "transfer-insufficient",
                "customer-subject",
                new BigDecimal("101.00")
        );

        assertEquals(AccountTransactionStatus.FAILED, response.debit().status());
        assertEquals("INSUFFICIENT_BALANCE", response.debit().failureReason());
        assertEquals(null, response.credit());
        assertEquals(new BigDecimal("100.00"), source.getAvailableBalance());
        assertEquals(new BigDecimal("100.00"), destination.getAvailableBalance());
        verify(transactionRepository).saveAndFlush(any(AccountTransaction.class));
        verify(transactionRepository, never()).saveAllAndFlush(any());
        verify(outboxService, never()).recordMoneyTransferred(
                any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void sameTransferIdempotencyKeyReturnsTheOriginalDebitAndCredit() {
        Account source = activeAccount();
        Account destination = activeAccount();
        destination.setAccountNumber("23456789012345");
        AccountTransaction debit = transferEntry(
                source, AccountTransactionType.TRANSFER_OUT, "transfer-1", "transfer-ref", destination.getAccountNumber()
        );
        AccountTransaction credit = transferEntry(
                destination, AccountTransactionType.TRANSFER_IN, "transfer-ref", "transfer-ref", source.getAccountNumber()
        );
        when(accountRepository.findByAccountNumberForUpdate(source.getAccountNumber()))
                .thenReturn(Optional.of(source));
        when(accountRepository.findByAccountNumberForUpdate(destination.getAccountNumber()))
                .thenReturn(Optional.of(destination));
        when(transactionRepository.findByAccount_IdAndIdempotencyKey(source.getId(), "transfer-1"))
                .thenReturn(Optional.of(debit));
        when(transactionRepository.findByTransferReferenceAndTransactionType(
                "transfer-ref", AccountTransactionType.TRANSFER_IN
        )).thenReturn(Optional.of(credit));

        var response = accountService.transfer(
                source.getAccountNumber(),
                destination.getAccountNumber(),
                123L,
                "transfer-1",
                "customer-subject",
                new BigDecimal("10.00")
        );

        assertEquals("transfer-ref", response.transferReference());
        assertEquals("debit-reference", response.debit().transactionReference());
        assertEquals("credit-reference", response.credit().transactionReference());
        assertEquals(new BigDecimal("100.00"), source.getAvailableBalance());
        verify(transactionRepository, never()).saveAllAndFlush(any());
        verify(outboxService, never()).recordMoneyTransferred(
                any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void transferCannotReuseIdempotencyKeyForAnotherDestination() {
        Account source = activeAccount();
        Account destination = activeAccount();
        destination.setAccountNumber("23456789012345");
        AccountTransaction prior = transferEntry(
                source,
                AccountTransactionType.TRANSFER_OUT,
                "transfer-1",
                "transfer-ref",
                "34567890123456"
        );
        when(accountRepository.findByAccountNumberForUpdate(source.getAccountNumber()))
                .thenReturn(Optional.of(source));
        when(accountRepository.findByAccountNumberForUpdate(destination.getAccountNumber()))
                .thenReturn(Optional.of(destination));
        when(transactionRepository.findByAccount_IdAndIdempotencyKey(source.getId(), "transfer-1"))
                .thenReturn(Optional.of(prior));

        assertThrows(
                com.pointbank.bankplatform.exception.IdempotencyKeyConflictException.class,
                () -> accountService.transfer(
                        source.getAccountNumber(),
                        destination.getAccountNumber(),
                        123L,
                        "transfer-1",
                        "customer-subject",
                        new BigDecimal("10.00")
                )
        );
        verify(transactionRepository, never()).saveAllAndFlush(any());
        verify(outboxService, never()).recordMoneyTransferred(
                any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void transferRejectsDifferentCurrenciesWithoutPostingEitherSide() {
        Account source = activeAccount();
        Account destination = activeAccount();
        destination.setAccountNumber("23456789012345");
        destination.setCurrencyCode("USD");
        when(accountRepository.findByAccountNumberForUpdate(source.getAccountNumber()))
                .thenReturn(Optional.of(source));
        when(accountRepository.findByAccountNumberForUpdate(destination.getAccountNumber()))
                .thenReturn(Optional.of(destination));
        when(transactionRepository.findByAccount_IdAndIdempotencyKey(source.getId(), "transfer-currency"))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceConflictException.class,
                () -> accountService.transfer(
                        source.getAccountNumber(),
                        destination.getAccountNumber(),
                        123L,
                        "transfer-currency",
                        "customer-subject",
                        new BigDecimal("10.00")
                )
        );
        verify(transactionRepository, never()).saveAndFlush(any(AccountTransaction.class));
        verify(transactionRepository, never()).saveAllAndFlush(any());
        verify(outboxService, never()).recordMoneyTransferred(
                any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void transferRejectsSendingToTheSameAccount() {
        Account source = activeAccount();

        assertThrows(
                InvalidRequestException.class,
                () -> accountService.transfer(
                        source.getAccountNumber(),
                        source.getAccountNumber(),
                        123L,
                        "transfer-self",
                        "customer-subject",
                        new BigDecimal("10.00")
                )
        );
        verify(accountRepository, never()).findByAccountNumberForUpdate(any());
    }

    @Test
    void sameIdempotencyKeyAndPayloadReturnsOriginalTransaction() {
        Account account = activeAccount();
        AccountTransaction transaction = new AccountTransaction();
        transaction.setTransactionReference("transaction-ref-1");
        transaction.setIdempotencyKey("deposit-1");
        transaction.setTransactionType(AccountTransactionType.DEPOSIT);
        transaction.setStatus(AccountTransactionStatus.COMPLETED);
        transaction.setAmount(new BigDecimal("10.00"));
        transaction.setCurrencyCode("INR");
        transaction.setAvailableBalanceAfter(new BigDecimal("110.00"));
        transaction.setLedgerBalanceAfter(new BigDecimal("110.00"));
        transaction.setAccount(account);
        when(accountRepository.findByAccountNumberForUpdate(account.getAccountNumber()))
                .thenReturn(Optional.of(account));
        when(transactionRepository.findByAccount_IdAndIdempotencyKey(account.getId(), "deposit-1"))
                .thenReturn(Optional.of(transaction));

        var response = accountService.deposit(
                account.getAccountNumber(), 123L, "deposit-1", "customer-subject", new BigDecimal("10.00")
        );

        assertEquals("transaction-ref-1", response.transactionReference());
        assertEquals(new BigDecimal("100.00"), account.getAvailableBalance());
        verify(transactionRepository, never()).saveAndFlush(any(AccountTransaction.class));
        verify(dailyTransactionLimitService, never()).assertWithinLimit(
                any(Account.class),
                any(AccountTransactionType.class),
                any(BigDecimal.class),
                any(java.time.Instant.class)
        );
    }

    @Test
    void idempotencyKeyCannotBeReusedWithDifferentAmount() {
        Account account = activeAccount();
        AccountTransaction transaction = new AccountTransaction();
        transaction.setTransactionType(AccountTransactionType.DEPOSIT);
        transaction.setAmount(new BigDecimal("10.00"));
        transaction.setAccount(account);
        when(accountRepository.findByAccountNumberForUpdate(account.getAccountNumber()))
                .thenReturn(Optional.of(account));
        when(transactionRepository.findByAccount_IdAndIdempotencyKey(account.getId(), "deposit-1"))
                .thenReturn(Optional.of(transaction));

        assertThrows(
                com.pointbank.bankplatform.exception.IdempotencyKeyConflictException.class,
                () -> accountService.deposit(
                        account.getAccountNumber(), 123L, "deposit-1", "customer-subject",
                        new BigDecimal("11.00")
                )
        );
    }

    @Test
    void customerCannotAccessAnotherCustomersAccount() {
        Account account = activeAccount();
        account.getCustomer().setCustomerId(456L);
        when(accountRepository.findByAccountNumber(account.getAccountNumber())).thenReturn(Optional.of(account));

        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> accountService.checkBalance(account.getAccountNumber(), 123L)
        );
    }

    @Test
    void accountCanBeActivatedAndMarksItsActiveState() {
        Account account = new Account();
        account.setAccountNumber("12345678901234");
        account.setAccountType(AccountType.SAVINGS);
        account.setCurrencyCode("INR");
        account.setAvailableBalance(BigDecimal.ZERO.setScale(2));
        account.setLedgerBalance(BigDecimal.ZERO.setScale(2));
        account.setCustomer(customer(KycStatus.VERIFIED));
        when(accountRepository.findByAccountNumberForUpdate(account.getAccountNumber()))
                .thenReturn(Optional.of(account));

        var response = accountService.transitionAccount(account.getAccountNumber(), AccountStatus.ACTIVE);

        assertEquals(AccountStatus.ACTIVE, response.accountStatus());
        assertEquals(true, account.isActive());
        org.junit.jupiter.api.Assertions.assertNotNull(response.activatedAt());
    }

    @Test
    void accountCannotBeClosedWithNonZeroBalance() {
        Account account = activeAccount();
        when(accountRepository.findByAccountNumberForUpdate(account.getAccountNumber()))
                .thenReturn(Optional.of(account));

        assertThrows(
                ResourceConflictException.class,
                () -> accountService.transitionAccount(account.getAccountNumber(), AccountStatus.CLOSED)
        );
    }

    @Test
    void transactionAmountCannotHaveFractionBeyondCurrencyPrecision() {
        Account account = activeAccount();
        when(accountRepository.findByAccountNumberForUpdate(account.getAccountNumber()))
                .thenReturn(Optional.of(account));

        assertThrows(
                com.pointbank.bankplatform.exception.InvalidTransactionAmountException.class,
                () -> accountService.deposit(
                        account.getAccountNumber(), 123L, "deposit-precision", "customer-subject",
                        new BigDecimal("1.001")
                )
        );
    }

    @Test
    void postingFailureDoesNotMutateBalances() {
        Account account = activeAccount();
        when(accountRepository.findByAccountNumberForUpdate(account.getAccountNumber()))
                .thenReturn(Optional.of(account));
        when(transactionRepository.findByAccount_IdAndIdempotencyKey(account.getId(), "deposit-failure"))
                .thenReturn(Optional.empty());
        when(transactionRepository.saveAndFlush(any(AccountTransaction.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("database failure"));

        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> accountService.deposit(
                        account.getAccountNumber(),
                        123L,
                        "deposit-failure",
                        "customer-subject",
                        new BigDecimal("25.00")
                )
        );

        assertEquals(new BigDecimal("100.00"), account.getAvailableBalance());
        assertEquals(new BigDecimal("100.00"), account.getLedgerBalance());
    }

    private Customer customer(KycStatus kycStatus) {
        Customer customer = new Customer();
        customer.setCustomerId(123L);
        customer.setKycStatus(kycStatus);
        customer.setCustomerStatus(CustomerStatus.VERIFIED);
        return customer;
    }

    private Account activeAccount() {
        Account account = new Account();
        account.setId(java.util.UUID.randomUUID());
        account.setAccountNumber("12345678901234");
        account.setAccountType(AccountType.SAVINGS);
        account.transitionTo(AccountStatus.ACTIVE, java.time.Instant.now());
        account.setCurrencyCode("INR");
        account.setAvailableBalance(new BigDecimal("100.00"));
        account.setLedgerBalance(new BigDecimal("100.00"));
        account.setCustomer(customer(KycStatus.VERIFIED));
        return account;
    }

    private AccountTransaction transferEntry(
            Account account,
            AccountTransactionType type,
            String idempotencyKey,
            String transferReference,
            String counterpartyAccountNumber
    ) {
        AccountTransaction transaction = new AccountTransaction();
        transaction.setTransactionReference(type == AccountTransactionType.TRANSFER_OUT
                ? "debit-reference"
                : "credit-reference");
        transaction.setIdempotencyKey(idempotencyKey);
        transaction.setTransactionType(type);
        transaction.setStatus(AccountTransactionStatus.COMPLETED);
        transaction.setAmount(new BigDecimal("10.00"));
        transaction.setCurrencyCode("INR");
        transaction.setAvailableBalanceAfter(new BigDecimal("90.00"));
        transaction.setLedgerBalanceAfter(new BigDecimal("90.00"));
        transaction.setCreatedAt(java.time.Instant.parse("2026-10-29T00:00:00Z"));
        transaction.setInitiatedBy("customer-subject");
        transaction.setAccount(account);
        transaction.setTransferReference(transferReference);
        transaction.setCounterpartyAccountNumber(counterpartyAccountNumber);
        return transaction;
    }
}
