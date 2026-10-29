package com.pointbank.bankplatform.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.pointbank.bankplatform.constant.AccountStatus;
import com.pointbank.bankplatform.constant.AccountType;
import com.pointbank.bankplatform.entity.Account;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByAccountId(Long accountId);

    Optional<Account> findByAccountNumber(String accountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.accountNumber = :accountNumber")
    Optional<Account> findByAccountNumberForUpdate(@Param("accountNumber") String accountNumber);

    boolean existsByAccountId(Long accountId);

    boolean existsByAccountNumber(String accountNumber);

    List<Account> findAllByCustomer_Id(UUID customerId);

    boolean existsByCustomer_CustomerIdAndAccountType(Long customerId, AccountType accountType);

    Page<Account> findAllByCustomer_CustomerId(Long customerId, Pageable pageable);

    Page<Account> findAllByAccountTypeAndAccountStatus(
            AccountType accountType,
            AccountStatus accountStatus,
            Pageable pageable
    );

    Page<Account> findAllByAccountType(AccountType accountType, Pageable pageable);

    Page<Account> findAllByAccountStatus(AccountStatus accountStatus, Pageable pageable);
}
