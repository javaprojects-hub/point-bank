package com.pointbank.bankplatform.repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pointbank.bankplatform.constant.AccountTransactionStatus;
import com.pointbank.bankplatform.constant.AccountTransactionType;
import com.pointbank.bankplatform.constant.AccountType;
import com.pointbank.bankplatform.entity.AccountTransaction;

public interface AccountTransactionRepository extends JpaRepository<AccountTransaction, UUID> {

    Optional<AccountTransaction> findByAccount_IdAndIdempotencyKey(UUID accountId, String idempotencyKey);

    Optional<AccountTransaction> findByTransactionReference(String transactionReference);

    Optional<AccountTransaction> findByTransferReferenceAndTransactionType(
            String transferReference,
            AccountTransactionType transactionType
    );

    Page<AccountTransaction> findAllByAccount_Id(UUID accountId, Pageable pageable);

    @Query("""
            select t
            from AccountTransaction t
            where t.account.id = :accountId
              and t.createdAt >= :periodStart
              and t.createdAt < :periodEnd
              and (:status is null or t.status = :status)
              and (:accountType is null or t.account.accountType = :accountType)
            """)
    Page<AccountTransaction> findStatementTransactions(
            @Param("accountId") UUID accountId,
            @Param("periodStart") Instant periodStart,
            @Param("periodEnd") Instant periodEnd,
            @Param("status") AccountTransactionStatus status,
            @Param("accountType") AccountType accountType,
            Pageable pageable
    );

    @Query("""
            select coalesce(sum(t.amount), 0)
            from AccountTransaction t
            where t.account.id = :accountId
              and t.transactionType = :type
              and t.status = :status
              and t.createdAt >= :windowStart
              and t.createdAt <= :windowEnd
            """)
    BigDecimal sumPostedAmount(
            @Param("accountId") UUID accountId,
            @Param("type") AccountTransactionType type,
            @Param("status") AccountTransactionStatus status,
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd
    );
}
