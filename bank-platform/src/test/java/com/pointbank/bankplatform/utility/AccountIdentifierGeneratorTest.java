package com.pointbank.bankplatform.utility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pointbank.bankplatform.entity.AccountIdentifierAllocation;
import com.pointbank.bankplatform.repository.AccountIdentifierAllocationRepository;
import com.pointbank.bankplatform.repository.AccountRepository;
import com.pointbank.bankplatform.service.AccountIdentifierAllocationService;
import org.junit.jupiter.api.Test;

class AccountIdentifierGeneratorTest {

    @Test
    void generatesFourteenDigitAccountNumberAndEightDigitAccountId() {
        AccountIdentifierAllocationRepository allocationRepository =
                mock(AccountIdentifierAllocationRepository.class);
        AccountIdentifierAllocationService allocationService =
                mock(AccountIdentifierAllocationService.class);
        AccountRepository accountRepository = mock(AccountRepository.class);
        AccountIdentifierAllocation allocation = mock(AccountIdentifierAllocation.class);
        when(accountRepository.existsByAccountNumber(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        when(allocationRepository.existsByAccountNumber(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        when(allocationService.reserve(org.mockito.ArgumentMatchers.anyString())).thenReturn(allocation);
        when(allocation.getId()).thenReturn(1L);

        AccountIdentifierGenerator.GeneratedAccountIdentifiers identifiers =
                new AccountIdentifierGenerator(
                        allocationRepository, allocationService, accountRepository
                ).generate();

        assertTrue(identifiers.accountNumber().matches("^[0-9]{14}$"));
        assertEquals(10_000_001L, identifiers.accountId());
        assertEquals(8, Long.toString(identifiers.accountId()).length());
    }
}
