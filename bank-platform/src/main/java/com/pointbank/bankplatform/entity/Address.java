package com.pointbank.bankplatform.entity;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.pointbank.bankplatform.constant.AddressType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "customer_address")
@Getter
@Setter
@NoArgsConstructor
public class Address extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull(message = "address type cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(name = "address_type", nullable = false)
    private AddressType addressType;

    @NotBlank(message = "address line 1 cannot be blank")
    @Size(max = 255)
    @Column(name = "address_line_1", nullable = false)
    private String addressLine1;

    @Size(max = 255)
    @Column(name = "address_line_2")
    private String addressLine2;

    @NotBlank(message = "city cannot be blank")
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String city;

    @NotBlank(message = "state or province cannot be blank")
    @Size(max = 100)
    @Column(name = "state_or_province", nullable = false, length = 100)
    private String stateOrProvince;

    @NotBlank(message = "postal code cannot be blank")
    @Size(max = 20)
    @Column(name = "postal_code", nullable = false, length = 20)
    private String postalCode;

    @NotBlank(message = "country code cannot be blank")
    @Pattern(regexp = "^[A-Z]{2}$", message = "country code must be a two-letter uppercase ISO code")
    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "primary_address", nullable = false)
    private boolean primary;

    @NotNull(message = "customer cannot be null")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    @JsonIgnore
    private Customer customer;
}
