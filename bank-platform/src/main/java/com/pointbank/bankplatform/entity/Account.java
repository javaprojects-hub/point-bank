package com.pointbank.bankplatform.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.pointbank.bankplatform.constant.AccountStatus;
import com.pointbank.bankplatform.constant.AccountType;

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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "bank_account",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_account_account_number", columnNames = "account_number"),
                @UniqueConstraint(name = "uk_account_account_id", columnNames = "account_id"),
                @UniqueConstraint(
                        name = "uk_account_customer_type",
                        columnNames = {"customer_id", "account_type"}
                )
        },
        indexes = {
                @Index(name = "idx_account_customer_id", columnList = "customer_id"),
                @Index(name = "idx_account_status_type", columnList = "account_status, account_type")
        }
)
@Check(constraints = "account_id BETWEEN 10000000 AND 99999999 AND available_balance >= 0 AND ledger_balance >= 0")
@Getter
@Setter
@NoArgsConstructor
public class Account extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull(message = "account id cannot be null")
    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;

    @NotBlank(message = "account number cannot be blank")
    @Pattern(regexp = "^[0-9]{14}$", message = "account number must contain exactly 14 digits")
    @Column(name = "account_number", nullable = false, updatable = false, length = 14)
    private String accountNumber;

    @NotNull(message = "account type cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 16)
    private AccountType accountType;

    @NotBlank(message = "currency code cannot be blank")
    @Pattern(regexp = "^[A-Z]{3}$", message = "currency code must be a three-letter uppercase ISO 4217 code")
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @NotNull(message = "account status cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 16)
    @Setter(AccessLevel.NONE)
    private AccountStatus accountStatus = AccountStatus.PENDING;

    @NotNull(message = "available balance cannot be null")
    @DecimalMin(value = "0.00", message = "available balance cannot be negative")
    @Column(name = "available_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal availableBalance = BigDecimal.ZERO;

    @NotNull(message = "ledger balance cannot be null")
    @DecimalMin(value = "0.00", message = "ledger balance cannot be negative")
    @Column(name = "ledger_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal ledgerBalance = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    @Setter(AccessLevel.NONE)
    private boolean isActive;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @jakarta.persistence.Version
    @Column(name = "version", nullable = false)
    private long version;

    @NotNull(message = "customer cannot be null")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    @JsonIgnore
    private Customer customer;

    public void transitionTo(AccountStatus targetStatus, Instant transitionTime) {
        if (targetStatus == null || transitionTime == null) {
            throw new IllegalArgumentException("Account status and transition time are required.");
        }

        boolean validTransition = switch (accountStatus) {
            case PENDING -> targetStatus == AccountStatus.ACTIVE || targetStatus == AccountStatus.BLOCKED;
            case ACTIVE -> targetStatus == AccountStatus.FROZEN
                    || targetStatus == AccountStatus.BLOCKED
                    || targetStatus == AccountStatus.CLOSED;
            case FROZEN -> targetStatus == AccountStatus.ACTIVE
                    || targetStatus == AccountStatus.BLOCKED
                    || targetStatus == AccountStatus.CLOSED;
            case BLOCKED, INACTIVE, SUSPENDED -> targetStatus == AccountStatus.CLOSED;
            case CLOSED -> false;
        };
        if (!validTransition) {
            throw new IllegalStateException(
                    "Cannot transition account from " + accountStatus + " to " + targetStatus + "."
            );
        }

        accountStatus = targetStatus;
        isActive = targetStatus == AccountStatus.ACTIVE;
        if (targetStatus == AccountStatus.ACTIVE && activatedAt == null) {
            activatedAt = transitionTime;
        }
        if (targetStatus == AccountStatus.CLOSED) {
            closedAt = transitionTime;
        }
    }
}
