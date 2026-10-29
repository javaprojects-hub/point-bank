package com.pointbank.bankplatform.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "account_identifier_allocation")
@Getter
@NoArgsConstructor
public class AccountIdentifierAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_number", unique = true, length = 14)
    private String accountNumber;

    public AccountIdentifierAllocation(String accountNumber) {
        this.accountNumber = accountNumber;
    }
}
