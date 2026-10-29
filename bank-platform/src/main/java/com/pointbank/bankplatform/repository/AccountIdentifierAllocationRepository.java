package com.pointbank.bankplatform.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pointbank.bankplatform.entity.AccountIdentifierAllocation;

public interface AccountIdentifierAllocationRepository
        extends JpaRepository<AccountIdentifierAllocation, Long> {

    boolean existsByAccountNumber(String accountNumber);
}
