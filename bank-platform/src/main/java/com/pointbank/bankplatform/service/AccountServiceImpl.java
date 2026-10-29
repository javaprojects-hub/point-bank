package com.pointbank.bankplatform.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.pointbank.bankplatform.constant.AccountStatus;
import com.pointbank.bankplatform.constant.AccountTransactionStatus;
import com.pointbank.bankplatform.constant.AccountTransactionType;
import com.pointbank.bankplatform.constant.AccountType;
import com.pointbank.bankplatform.constant.CustomerStatus;
import com.pointbank.bankplatform.constant.KycStatus;
import com.pointbank.bankplatform.dto.AccountResponse;
import com.pointbank.bankplatform.dto.AccountTransferResponse;
import com.pointbank.bankplatform.dto.AccountTransactionResponse;
import com.pointbank.bankplatform.entity.Account;
import com.pointbank.bankplatform.entity.AccountTransaction;
import com.pointbank.bankplatform.entity.Customer;
import com.pointbank.bankplatform.exception.InvalidRequestException;
import com.pointbank.bankplatform.exception.IdempotencyKeyConflictException;
import com.pointbank.bankplatform.exception.AccountNotActiveException;
import com.pointbank.bankplatform.exception.InsufficientBalanceException;
import com.pointbank.bankplatform.exception.ResourceConflictException;
import com.pointbank.bankplatform.exception.ResourceNotFoundException;
import com.pointbank.bankplatform.repository.AccountRepository;
import com.pointbank.bankplatform.repository.AccountTransactionRepository;
import com.pointbank.bankplatform.repository.CustomerRepository;
import com.pointbank.bankplatform.utility.AccountIdentifierGenerator;

@Service
@Transactional(readOnly = true)
public class AccountServiceImpl implements AccountService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountServiceImpl.class);
    private static final String DEFAULT_CURRENCY = "INR";
    private static final BigDecimal MAX_ACCOUNT_BALANCE = new BigDecimal("99999999999999999.99");
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_STATEMENT_TRANSACTIONS = 5_000;
    private static final Sort TRANSACTION_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final AccountRepository accountRepository;
    private final AccountTransactionRepository transactionRepository;
    private final CustomerRepository customerRepository;
    private final AccountIdentifierGenerator identifierGenerator;
    private final DailyTransactionLimitService dailyTransactionLimitService;
    private final Clock clock;

    @Autowired
    public AccountServiceImpl(
            AccountRepository accountRepository,
            AccountTransactionRepository transactionRepository,
            CustomerRepository customerRepository,
            AccountIdentifierGenerator identifierGenerator,
            DailyTransactionLimitService dailyTransactionLimitService,
            Clock clock
    ) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.customerRepository = customerRepository;
        this.identifierGenerator = identifierGenerator;
        this.dailyTransactionLimitService = dailyTransactionLimitService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AccountResponse openAccount(Long customerId, AccountType accountType) {
        if (customerId == null || accountType == null) {
            throw new InvalidRequestException("Customer and account type are required.");
        }

        Customer customer = customerRepository.findByCustomerIdForUpdate(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found."));
        if (customer.getKycStatus() != KycStatus.VERIFIED
                || (customer.getCustomerStatus() != CustomerStatus.VERIFIED
                && customer.getCustomerStatus() != CustomerStatus.ACTIVE)) {
            LOGGER.warn("Account opening rejected because customer eligibility requirements were not met.");
            throw new ResourceConflictException("Verified KYC and an eligible customer status are required.");
        }
        if (accountRepository.existsByCustomer_CustomerIdAndAccountType(customerId, accountType)) {
            LOGGER.warn("Duplicate account type rejected: accountType={}", accountType);
            throw new ResourceConflictException("Customer already has an account of this type.");
        }

        AccountIdentifierGenerator.GeneratedAccountIdentifiers identifiers = identifierGenerator.generate();
        Account account = new Account();
        account.setAccountId(identifiers.accountId());
        account.setAccountNumber(identifiers.accountNumber());
        account.setAccountType(accountType);
        account.setCurrencyCode(DEFAULT_CURRENCY);
        account.setAvailableBalance(BigDecimal.ZERO.setScale(2));
        account.setLedgerBalance(BigDecimal.ZERO.setScale(2));
        account.setCustomer(customer);
        try {
            Account savedAccount = accountRepository.saveAndFlush(account);
            logAfterCommit(
                    "Account opened: accountType={}, accountStatus={}",
                    savedAccount.getAccountType(),
                    savedAccount.getAccountStatus()
            );
            return toAccountResponse(savedAccount);
        } catch (DataIntegrityViolationException exception) {
            throw new ResourceConflictException(
                    "The account conflicts with an existing account or account type."
            );
        }
    }

    @Override
    public AccountResponse getAccount(String accountNumber, Long ownerCustomerId) {
        Account account = findAccount(accountNumber);
        assertOwner(account, ownerCustomerId);
        return toAccountResponse(account);
    }

    @Override
    public Page<AccountResponse> getCustomerAccounts(Long customerId, Pageable pageable) {
        if (customerId == null || customerId <= 0) {
            throw new InvalidRequestException("Customer ID must be positive.");
        }
        return accountRepository.findAllByCustomer_CustomerId(customerId, safePageable(pageable))
                .map(this::toAccountResponse);
    }

    @Override
    public Page<AccountResponse> getAccounts(AccountType type, AccountStatus status, Pageable pageable) {
        Pageable safePageable = safePageable(pageable);
        if (type != null && status != null) {
            return accountRepository.findAllByAccountTypeAndAccountStatus(type, status, safePageable)
                    .map(this::toAccountResponse);
        }
        if (type != null) {
            return accountRepository.findAllByAccountType(type, safePageable).map(this::toAccountResponse);
        }
        if (status != null) {
            return accountRepository.findAllByAccountStatus(status, safePageable).map(this::toAccountResponse);
        }
        return accountRepository.findAll(safePageable).map(this::toAccountResponse);
    }

    @Override
    @Transactional
    public AccountResponse transitionAccount(String accountNumber, AccountStatus targetStatus) {
        if (targetStatus == null) {
            throw new InvalidRequestException("Target account status is required.");
        }
        Account account = findAccountForUpdate(accountNumber);
        if (account.getAccountStatus() == targetStatus) {
            return toAccountResponse(account);
        }
        if (targetStatus == AccountStatus.CLOSED
                && (account.getAvailableBalance().signum() != 0 || account.getLedgerBalance().signum() != 0)) {
            throw new ResourceConflictException("An account can be closed only when its balances are zero.");
        }
        try {
            account.transitionTo(targetStatus, clock.instant());
        } catch (IllegalStateException exception) {
            throw new ResourceConflictException(exception.getMessage());
        }
        logAfterCommit(
                "Account lifecycle updated: accountType={}, accountStatus={}",
                account.getAccountType(),
                account.getAccountStatus()
        );
        return toAccountResponse(account);
    }

    @Override
    public AccountResponse checkBalance(String accountNumber, Long ownerCustomerId) {
        Account account = findAccount(accountNumber);
        assertOwner(account, ownerCustomerId);
        return toAccountResponse(account);
    }

    @Override
    @Transactional
    public AccountTransactionResponse deposit(
            String accountNumber,
            Long ownerCustomerId,
            String idempotencyKey,
            String initiatedBy,
            BigDecimal amount
    ) {
        return processTransaction(
                accountNumber, ownerCustomerId, idempotencyKey, initiatedBy, amount, AccountTransactionType.DEPOSIT
        );
    }

    @Override
    @Transactional
    public AccountTransactionResponse withdraw(
            String accountNumber,
            Long ownerCustomerId,
            String idempotencyKey,
            String initiatedBy,
            BigDecimal amount
    ) {
        return processTransaction(
                accountNumber, ownerCustomerId, idempotencyKey, initiatedBy, amount, AccountTransactionType.WITHDRAWAL
        );
    }

    @Override
    @Transactional
    public AccountTransferResponse transfer(
            String sourceAccountNumber,
            String destinationAccountNumber,
            Long ownerCustomerId,
            String idempotencyKey,
            String initiatedBy,
            BigDecimal requestedAmount
    ) {
        validateTransactionRequest(idempotencyKey, initiatedBy);
        if (sourceAccountNumber == null || destinationAccountNumber == null
                || sourceAccountNumber.equals(destinationAccountNumber)) {
            throw new InvalidRequestException("Source and destination accounts must be different.");
        }

        String firstAccountNumber = sourceAccountNumber.compareTo(destinationAccountNumber) < 0
                ? sourceAccountNumber
                : destinationAccountNumber;
        String secondAccountNumber = sourceAccountNumber.compareTo(destinationAccountNumber) < 0
                ? destinationAccountNumber
                : sourceAccountNumber;
        Account firstAccount = findAccountForUpdate(firstAccountNumber);
        Account secondAccount = findAccountForUpdate(secondAccountNumber);
        Account source = sourceAccountNumber.equals(firstAccountNumber) ? firstAccount : secondAccount;
        Account destination = destinationAccountNumber.equals(firstAccountNumber) ? firstAccount : secondAccount;
        assertOwner(source, ownerCustomerId);
        BigDecimal amount = normalizeAmount(requestedAmount);

        AccountTransaction prior = transactionRepository
                .findByAccount_IdAndIdempotencyKey(source.getId(), idempotencyKey)
                .orElse(null);
        if (prior != null) {
            if (prior.getTransactionType() != AccountTransactionType.TRANSFER_OUT
                    || prior.getAmount().compareTo(amount) != 0
                    || !destinationAccountNumber.equals(prior.getCounterpartyAccountNumber())) {
                throw new IdempotencyKeyConflictException();
            }
            return toTransferResponse(prior);
        }

        if (!source.getCurrencyCode().equals(destination.getCurrencyCode())) {
            throw new ResourceConflictException("Transfers between different currencies are not supported.");
        }

        Instant transactionTime = clock.instant();
        String transferReference = UUID.randomUUID().toString();
        BigDecimal sourceBalanceAfter = source.getAvailableBalance();
        BigDecimal sourceLedgerBalanceAfter = source.getLedgerBalance();
        BigDecimal destinationBalanceAfter = destination.getAvailableBalance();
        BigDecimal destinationLedgerBalanceAfter = destination.getLedgerBalance();
        String failureReason = null;
        if (source.getAccountStatus() != AccountStatus.ACTIVE
                || destination.getAccountStatus() != AccountStatus.ACTIVE) {
            failureReason = "ACCOUNT_NOT_ACTIVE";
        } else if (source.getAvailableBalance().compareTo(amount) < 0
                || source.getLedgerBalance().compareTo(amount) < 0) {
            failureReason = "INSUFFICIENT_BALANCE";
        } else {
            dailyTransactionLimitService.assertWithinLimit(
                    source, AccountTransactionType.TRANSFER_OUT, amount, transactionTime
            );
            dailyTransactionLimitService.assertWithinLimit(
                    destination, AccountTransactionType.TRANSFER_IN, amount, transactionTime
            );
            sourceBalanceAfter = source.getAvailableBalance().subtract(amount);
            sourceLedgerBalanceAfter = source.getLedgerBalance().subtract(amount);
            destinationBalanceAfter = destination.getAvailableBalance().add(amount);
            destinationLedgerBalanceAfter = destination.getLedgerBalance().add(amount);
            if (destinationBalanceAfter.compareTo(MAX_ACCOUNT_BALANCE) > 0
                    || destinationLedgerBalanceAfter.compareTo(MAX_ACCOUNT_BALANCE) > 0) {
                failureReason = "BALANCE_LIMIT_EXCEEDED";
            }
        }

        AccountTransaction debit = createTransferEntry(
                source,
                destination,
                AccountTransactionType.TRANSFER_OUT,
                idempotencyKey,
                UUID.randomUUID().toString(),
                amount,
                failureReason == null ? sourceBalanceAfter : source.getAvailableBalance(),
                failureReason == null ? sourceLedgerBalanceAfter : source.getLedgerBalance(),
                initiatedBy,
                transactionTime,
                transferReference,
                failureReason
        );
        if (failureReason != null) {
            transactionRepository.saveAndFlush(debit);
            return toTransferResponse(debit);
        }

        AccountTransaction credit = createTransferEntry(
                destination,
                source,
                AccountTransactionType.TRANSFER_IN,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                amount,
                destinationBalanceAfter,
                destinationLedgerBalanceAfter,
                initiatedBy,
                transactionTime,
                transferReference,
                null
        );
        transactionRepository.saveAllAndFlush(List.of(debit, credit));
        source.setAvailableBalance(sourceBalanceAfter);
        source.setLedgerBalance(sourceLedgerBalanceAfter);
        destination.setAvailableBalance(destinationBalanceAfter);
        destination.setLedgerBalance(destinationLedgerBalanceAfter);
        logAfterCommit(
                "Internal account transfer completed: transferReference={}, currency={}",
                transferReference,
                source.getCurrencyCode()
        );
        return new AccountTransferResponse(
                transferReference,
                toTransactionResponse(debit),
                toTransactionResponse(credit)
        );
    }

    @Override
    public AccountTransferResponse getTransferByIdempotencyKey(
            String sourceAccountNumber,
            Long ownerCustomerId,
            String idempotencyKey
    ) {
        Account source = findAccount(sourceAccountNumber);
        assertOwner(source, ownerCustomerId);
        AccountTransaction debit = transactionRepository
                .findByAccount_IdAndIdempotencyKey(source.getId(), idempotencyKey)
                .filter(transaction -> transaction.getTransactionType() == AccountTransactionType.TRANSFER_OUT)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer request not found."));
        return toTransferResponse(debit);
    }

    @Override
    public Page<AccountTransactionResponse> getTransactionHistory(
            String accountNumber,
            Long ownerCustomerId,
            Pageable pageable
    ) {
        Account account = findAccount(accountNumber);
        assertOwner(account, ownerCustomerId);
        return transactionRepository.findAllByAccount_Id(account.getId(), safePageable(pageable))
                .map(this::toTransactionResponse);
    }

    @Override
    public List<AccountTransactionResponse> getStatementTransactions(
            String accountNumber,
            Long ownerCustomerId,
            Instant periodStart,
            Instant periodEnd,
            AccountType accountType,
            AccountTransactionStatus status
    ) {
        if (periodStart == null || periodEnd == null || !periodStart.isBefore(periodEnd)) {
            throw new InvalidRequestException("A valid statement period is required.");
        }

        Account account = findAccount(accountNumber);
        assertOwner(account, ownerCustomerId);
        Page<AccountTransaction> statementPage = transactionRepository.findStatementTransactions(
                account.getId(),
                periodStart,
                periodEnd,
                status,
                accountType,
                PageRequest.of(0, MAX_STATEMENT_TRANSACTIONS + 1, TRANSACTION_SORT)
        );
        if (statementPage.getTotalElements() > MAX_STATEMENT_TRANSACTIONS) {
            throw new InvalidRequestException(
                    "The statement contains too many transactions to export. Narrow the date range or filters."
            );
        }
        return statementPage.getContent().stream().map(this::toTransactionResponse).toList();
    }

    @Override
    public AccountTransactionResponse getTransaction(String transactionReference, Long ownerCustomerId) {
        AccountTransaction transaction = transactionRepository.findByTransactionReference(transactionReference)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found."));
        assertOwner(transaction.getAccount(), ownerCustomerId);
        return toTransactionResponse(transaction);
    }

    @Override
    public AccountTransactionResponse getTransactionByIdempotencyKey(
            String accountNumber,
            Long ownerCustomerId,
            String idempotencyKey
    ) {
        Account account = findAccount(accountNumber);
        assertOwner(account, ownerCustomerId);
        return transactionRepository.findByAccount_IdAndIdempotencyKey(account.getId(), idempotencyKey)
                .map(this::toTransactionResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction request not found."));
    }

    private AccountTransactionResponse processTransaction(
            String accountNumber,
            Long ownerCustomerId,
            String idempotencyKey,
            String initiatedBy,
            BigDecimal requestedAmount,
            AccountTransactionType type
    ) {
        validateTransactionRequest(idempotencyKey, initiatedBy);
        
        Account account = findAccountForUpdate(accountNumber);
        assertOwner(account, ownerCustomerId);
        BigDecimal amount = normalizeAmount(requestedAmount);

        AccountTransaction prior = transactionRepository
                .findByAccount_IdAndIdempotencyKey(account.getId(), idempotencyKey)
                .orElse(null);
        if (prior != null) {
            if (prior.getTransactionType() != type || prior.getAmount().compareTo(amount) != 0) {
                throw new IdempotencyKeyConflictException();
            }
            LOGGER.info("Duplicate idempotent transaction request returned: transactionType={}", type);
            return toTransactionResponse(prior);
        }

        AccountTransaction transaction = new AccountTransaction();
        transaction.setTransactionReference(UUID.randomUUID().toString());
        transaction.setIdempotencyKey(idempotencyKey);
        transaction.setTransactionType(type);
        transaction.setAmount(amount);
        transaction.setCurrencyCode(account.getCurrencyCode());
        Instant transactionTime = clock.instant();
        transaction.setCreatedAt(transactionTime);
        transaction.setInitiatedBy(initiatedBy);
        transaction.setAccount(account);

        BigDecimal nextAvailableBalance = account.getAvailableBalance();
        BigDecimal nextLedgerBalance = account.getLedgerBalance();
        if (account.getAccountStatus() != AccountStatus.ACTIVE) {
            AccountNotActiveException rejection = new AccountNotActiveException(account.getAccountStatus());
            transaction.setStatus(AccountTransactionStatus.FAILED);
            transaction.setFailureReason(rejection.getCode());
            LOGGER.warn("Transaction rejected because account is not active: accountStatus={}", account.getAccountStatus());
        } else if (type == AccountTransactionType.WITHDRAWAL
                && account.getAvailableBalance().compareTo(amount) < 0) {
            InsufficientBalanceException rejection = new InsufficientBalanceException();
            transaction.setStatus(AccountTransactionStatus.FAILED);
            transaction.setFailureReason(rejection.getCode());
            LOGGER.warn("Transaction rejected for insufficient funds: transactionType={}", type);
        } else {
            dailyTransactionLimitService.assertWithinLimit(account, type, amount, transactionTime);
            BigDecimal balanceChange = type == AccountTransactionType.DEPOSIT ? amount : amount.negate();
            nextAvailableBalance = account.getAvailableBalance().add(balanceChange);
            nextLedgerBalance = account.getLedgerBalance().add(balanceChange);
            if (nextAvailableBalance.compareTo(MAX_ACCOUNT_BALANCE) > 0
                    || nextLedgerBalance.compareTo(MAX_ACCOUNT_BALANCE) > 0) {
                transaction.setStatus(AccountTransactionStatus.FAILED);
                transaction.setFailureReason("BALANCE_LIMIT_EXCEEDED");
            } else {
                transaction.setStatus(AccountTransactionStatus.COMPLETED);
            }
        }

        transaction.setAvailableBalanceAfter(nextAvailableBalance);
        transaction.setLedgerBalanceAfter(nextLedgerBalance);
        AccountTransaction savedTransaction = transactionRepository.saveAndFlush(transaction);
        if (savedTransaction.getStatus() == AccountTransactionStatus.COMPLETED) {
            account.setAvailableBalance(nextAvailableBalance);
            account.setLedgerBalance(nextLedgerBalance);
            logAfterCommit(
                    "Financial transaction posted: transactionReference={}, transactionType={}, transactionStatus={}, currency={}, channel=SIMULATED",
                    savedTransaction.getTransactionReference(),
                    savedTransaction.getTransactionType(),
                    savedTransaction.getStatus(),
                    savedTransaction.getCurrencyCode()
            );
        }
        return toTransactionResponse(savedTransaction);
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new com.pointbank.bankplatform.exception.InvalidTransactionAmountException(
                    "Amount must be greater than zero."
            );
        }
        try {
            BigDecimal normalizedAmount = amount.setScale(2, RoundingMode.UNNECESSARY);
            if (normalizedAmount.compareTo(MAX_ACCOUNT_BALANCE) > 0) {
                throw new com.pointbank.bankplatform.exception.InvalidTransactionAmountException(
                        "Amount exceeds the supported account balance limit."
                );
            }
            return normalizedAmount;
        } catch (ArithmeticException exception) {
            throw new com.pointbank.bankplatform.exception.InvalidTransactionAmountException(
                    "Amount cannot have more than two decimal places."
            );
        }
    }

    private Account findAccount(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found."));
    }

    private Account findAccountForUpdate(String accountNumber) {
        return accountRepository.findByAccountNumberForUpdate(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found."));
    }

    private void assertOwner(Account account, Long ownerCustomerId) {
        if (ownerCustomerId != null
                && !ownerCustomerId.equals(account.getCustomer().getCustomerId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Account access is not authorized."
            );
        }
    }

    private Pageable safePageable(Pageable pageable) {
        int page = pageable == null ? 0 : Math.max(0, pageable.getPageNumber());
        int size = pageable == null ? 20 : Math.min(Math.max(1, pageable.getPageSize()), MAX_PAGE_SIZE);
        return PageRequest.of(page, size, TRANSACTION_SORT);
    }

    private void logAfterCommit(String message, Object... arguments) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    LOGGER.info(message, arguments);
                }
            });
        } else {
            LOGGER.info(message, arguments);
        }
    }

    private AccountResponse toAccountResponse(Account account) {
        Instant createdAt = account.getCreatedAt() == null
                ? null
                : account.getCreatedAt().toInstant(ZoneOffset.UTC);
        return new AccountResponse(
                account.getAccountId(),
                account.getAccountNumber(),
                account.getAccountType(),
                account.getAccountStatus(),
                account.getCurrencyCode(),
                account.getAvailableBalance(),
                account.getLedgerBalance(),
                createdAt,
                account.getActivatedAt(),
                account.getClosedAt()
        );
    }

    private AccountTransactionResponse toTransactionResponse(AccountTransaction transaction) {
        return new AccountTransactionResponse(
                transaction.getTransactionReference(),
                transaction.getAccount().getAccountNumber(),
                transaction.getTransactionType(),
                transaction.getStatus(),
                transaction.getAmount(),
                transaction.getCurrencyCode(),
                transaction.getAvailableBalanceAfter(),
                transaction.getLedgerBalanceAfter(),
                transaction.getTransactionType() == AccountTransactionType.TRANSFER_OUT
                                || transaction.getTransactionType() == AccountTransactionType.TRANSFER_IN
                        ? "INTERNAL"
                        : "SIMULATED",
                transaction.getFailureReason(),
                transaction.getCreatedAt(),
                transaction.getTransferReference(),
                transaction.getCounterpartyAccountNumber()
        );
    }

    private AccountTransferResponse toTransferResponse(AccountTransaction debit) {
        AccountTransaction credit = debit.getStatus() == AccountTransactionStatus.COMPLETED
                ? transactionRepository.findByTransferReferenceAndTransactionType(
                        debit.getTransferReference(), AccountTransactionType.TRANSFER_IN
                ).orElseThrow(() -> new IllegalStateException(
                        "A completed transfer is missing its matching credit posting."
                ))
                : null;
        return new AccountTransferResponse(
                debit.getTransferReference(),
                toTransactionResponse(debit),
                credit == null ? null : toTransactionResponse(credit)
        );
    }

    private AccountTransaction createTransferEntry(
            Account account,
            Account counterparty,
            AccountTransactionType type,
            String idempotencyKey,
            String transactionReference,
            BigDecimal amount,
            BigDecimal availableBalanceAfter,
            BigDecimal ledgerBalanceAfter,
            String initiatedBy,
            Instant transactionTime,
            String transferReference,
            String failureReason
    ) {
        AccountTransaction transaction = new AccountTransaction();
        transaction.setTransactionReference(transactionReference);
        transaction.setIdempotencyKey(idempotencyKey);
        transaction.setTransactionType(type);
        transaction.setStatus(failureReason == null
                ? AccountTransactionStatus.COMPLETED
                : AccountTransactionStatus.FAILED);
        transaction.setAmount(amount);
        transaction.setCurrencyCode(account.getCurrencyCode());
        transaction.setAvailableBalanceAfter(availableBalanceAfter);
        transaction.setLedgerBalanceAfter(ledgerBalanceAfter);
        transaction.setCreatedAt(transactionTime);
        transaction.setInitiatedBy(initiatedBy);
        transaction.setAccount(account);
        transaction.setTransferReference(transferReference);
        transaction.setCounterpartyAccountNumber(counterparty.getAccountNumber());
        transaction.setFailureReason(failureReason);
        return transaction;
    }

    private void validateTransactionRequest(String idempotencyKey, String initiatedBy) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new InvalidRequestException("A valid idempotency key of at most 100 characters is required.");
        }
        if (initiatedBy == null || initiatedBy.isBlank()) {
            throw new InvalidRequestException("Transaction actor is required.");
        }
        if (initiatedBy.length() > 255) {
            throw new InvalidRequestException("Transaction actor reference is too long.");
        }
    }
}
