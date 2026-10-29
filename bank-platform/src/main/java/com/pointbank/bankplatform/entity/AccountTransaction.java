package com.pointbank.bankplatform.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.pointbank.bankplatform.constant.AccountTransactionStatus;
import com.pointbank.bankplatform.constant.AccountTransactionType;

import org.hibernate.annotations.Check;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.AccessLevel;

@Entity
@Table(
        name = "account_transaction",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_account_transaction_reference", columnNames = "transaction_reference"),
                @UniqueConstraint(
                        name = "uk_account_transaction_idempotency",
                        columnNames = {"account_id", "idempotency_key"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_transaction_limit_lookup",
                        columnList = "account_id, transaction_type, transaction_status, created_at"
                ),
                @Index(name = "idx_transaction_transfer_reference", columnList = "transfer_reference")
        }
)
@Check(constraints = "amount > 0 AND available_balance_after >= 0 AND ledger_balance_after >= 0")
@Getter
@Setter
@NoArgsConstructor
public class AccountTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "transaction_reference", nullable = false, updatable = false, length = 36)
    private String transactionReference;

    @NotNull
    @Size(max = 100)
    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 16, updatable = false)
    private AccountTransactionType transactionType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_status", nullable = false, length = 16)
    @Setter(AccessLevel.NONE)
    private AccountTransactionStatus status;

    @NotNull
    @DecimalMin(value = "0.01")
    @Column(name = "amount", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @NotNull
    @Column(name = "currency_code", nullable = false, length = 3, updatable = false)
    private String currencyCode;

    @NotNull
    @Column(name = "available_balance_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal availableBalanceAfter;

    @NotNull
    @Column(name = "ledger_balance_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal ledgerBalanceAfter;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @NotNull
    @Column(name = "initiated_by", nullable = false, updatable = false, length = 255)
    private String initiatedBy;

    @Column(name = "failure_reason", length = 100)
    private String failureReason;

    @Column(name = "transfer_reference", length = 36, updatable = false)
    private String transferReference;

    @Column(name = "counterparty_account_number", length = 14, updatable = false)
    private String counterpartyAccountNumber;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private Account account;

    public void setStatus(AccountTransactionStatus status) {
        if (this.status != null) {
            throw new IllegalStateException("Transaction status is immutable once initialized.");
        }
        this.status = status;
    }
}
