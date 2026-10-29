package com.pointbank.bankplatform.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.List;

import com.pointbank.bankplatform.constant.AccountStatus;
import com.pointbank.bankplatform.constant.AccountTransactionStatus;
import com.pointbank.bankplatform.constant.AccountType;
import com.pointbank.bankplatform.dto.AccountResponse;
import com.pointbank.bankplatform.dto.AccountTransferResponse;
import com.pointbank.bankplatform.dto.AccountTransactionResponse;
import com.pointbank.bankplatform.entity.AccountTransaction;

public interface AccountService {

    AccountResponse openAccount(Long customerId, AccountType accountType);

    AccountResponse getAccount(String accountNumber, Long ownerCustomerId);

    Page<AccountResponse> getCustomerAccounts(Long customerId, Pageable pageable);

    Page<AccountResponse> getAccounts(AccountType type, AccountStatus status, Pageable pageable);

    AccountResponse transitionAccount(String accountNumber, AccountStatus targetStatus);

    AccountResponse checkBalance(String accountNumber, Long ownerCustomerId);

    AccountTransactionResponse deposit(
            String accountNumber,
            Long ownerCustomerId,
            String idempotencyKey,
            String initiatedBy,
            java.math.BigDecimal amount
    );

    AccountTransactionResponse withdraw(
            String accountNumber,
            Long ownerCustomerId,
            String idempotencyKey,
            String initiatedBy,
            java.math.BigDecimal amount
    );

    AccountTransferResponse transfer(
            String sourceAccountNumber,
            String destinationAccountNumber,
            Long ownerCustomerId,
            String idempotencyKey,
            String initiatedBy,
            java.math.BigDecimal amount
    );

    AccountTransferResponse getTransferByIdempotencyKey(
            String sourceAccountNumber,
            Long ownerCustomerId,
            String idempotencyKey
    );

    Page<AccountTransactionResponse> getTransactionHistory(
            String accountNumber,
            Long ownerCustomerId,
            Pageable pageable
    );

    List<AccountTransactionResponse> getStatementTransactions(
            String accountNumber,
            Long ownerCustomerId,
            Instant periodStart,
            Instant periodEnd,
            AccountType accountType,
            AccountTransactionStatus status
    );

    AccountTransactionResponse getTransaction(String transactionReference, Long ownerCustomerId);

    AccountTransactionResponse getTransactionByIdempotencyKey(
            String accountNumber,
            Long ownerCustomerId,
            String idempotencyKey
    );
}
