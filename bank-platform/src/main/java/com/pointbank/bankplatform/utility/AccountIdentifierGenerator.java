package com.pointbank.bankplatform.utility;

import java.security.SecureRandom;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.pointbank.bankplatform.entity.AccountIdentifierAllocation;
import com.pointbank.bankplatform.repository.AccountRepository;
import com.pointbank.bankplatform.repository.AccountIdentifierAllocationRepository;
import com.pointbank.bankplatform.service.AccountIdentifierAllocationService;

@Service
public class AccountIdentifierGenerator {

    private static final long MAX_SEQUENCE_VALUE = 89_999_999L;
    private static final long ACCOUNT_ID_OFFSET = 10_000_000L;
    private static final long ACCOUNT_NUMBER_MIN = 10_000_000_000_000L;
    private static final long ACCOUNT_NUMBER_RANGE = 90_000_000_000_000L;
    private static final int MAX_COLLISION_RETRIES = 10;

    private final SecureRandom secureRandom = new SecureRandom();
    private final AccountIdentifierAllocationRepository allocationRepository;
    private final AccountIdentifierAllocationService allocationService;
    private final AccountRepository accountRepository;

    public AccountIdentifierGenerator(
            AccountIdentifierAllocationRepository allocationRepository,
            AccountIdentifierAllocationService allocationService,
            AccountRepository accountRepository) {
        this.allocationRepository = allocationRepository;
        this.allocationService = allocationService;
        this.accountRepository = accountRepository;
    }

    public GeneratedAccountIdentifiers generate() {
        
        for (int attempt = 0; attempt < MAX_COLLISION_RETRIES; attempt++) {
            String accountNumber = generateAccountNumber();
            if (accountRepository.existsByAccountNumber(accountNumber)
                    || allocationRepository.existsByAccountNumber(accountNumber)) {
                continue;
            }

            try {
                AccountIdentifierAllocation allocation = allocationService.reserve(accountNumber);
                Long allocatedId = allocation.getId();
                if (allocatedId == null || allocatedId < 1 || allocatedId > MAX_SEQUENCE_VALUE) {
                    throw new IllegalStateException("Eight-digit account ID capacity has been exhausted");
                }

                long accountId = ACCOUNT_ID_OFFSET + allocatedId;
                return new GeneratedAccountIdentifiers(accountNumber, accountId);
            } catch (DataIntegrityViolationException exception) {
                if (!allocationRepository.existsByAccountNumber(accountNumber)) {
                    throw exception;
                }
            }
        }

        throw new IllegalStateException("Unable to allocate a unique account number after multiple attempts");
    }

    private String generateAccountNumber() {

        return Long.toString(ACCOUNT_NUMBER_MIN + secureRandom.nextLong(ACCOUNT_NUMBER_RANGE));
    }

    public record GeneratedAccountIdentifiers(String accountNumber, long accountId) {
    }
}
