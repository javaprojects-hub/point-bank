package com.pointbank.bankplatform.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.pointbank.bankplatform.entity.AccountIdentifierAllocation;
import com.pointbank.bankplatform.repository.AccountIdentifierAllocationRepository;

@Service
public class AccountIdentifierAllocationService {

    private final AccountIdentifierAllocationRepository allocationRepository;

    public AccountIdentifierAllocationService(AccountIdentifierAllocationRepository allocationRepository) {
        this.allocationRepository = allocationRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AccountIdentifierAllocation reserve(String accountNumber) {
        return allocationRepository.saveAndFlush(new AccountIdentifierAllocation(accountNumber));
    }
}
