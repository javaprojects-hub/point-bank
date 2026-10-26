package com.pointbank.bankplatform.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.pointbank.bankplatform.constant.CustomerStatus;
import com.pointbank.bankplatform.constant.KycStatus;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "customer",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_customer_identity",
                columnNames = {"identity_issuer", "identity_subject"}
        ),
        indexes = {
                @Index(name = "idx_customer_id", columnList = "customer_id"),
                @Index(name = "idx_mobile_number", columnList = "mobile_number")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Customer extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull(message = "customerId cannot be null")
    @Column(name = "customer_id", unique = true, updatable = false)
    private Long customerId;

    @NotBlank(message = "first name cannot be blank")
    @Size(max = 100)
    @Column(name = "first_name")
    private String firstName;

    @Size(max = 100)
    @Column(name = "middle_name")
    private String middleName;

    @NotBlank(message = "last name cannot be blank")
    @Size(max = 100)
    @Column(name = "last_name")
    private String lastName;

    @NotBlank(message = "gender cannot be blank")
    private String gender;

    @NotNull(message = "date of birth cannot be null")
    @Past(message = "date of birth must be in the past")
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @NotBlank(message = "mobile number cannot be blank")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "mobile number must be 10 digits and start with 6 through 9")
    @Column(name = "mobile_number", unique = true)
    private String mobileNumber;

    @NotBlank(message = "email cannot be blank")
    @Email
    @Column(name = "email_id", unique = true)
    private String email;

    @Column(name = "mobile_number_verified", nullable = false)
    private boolean mobileNumberVerified;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    @Setter(lombok.AccessLevel.NONE)
    private List<Address> addresses = new ArrayList<>();

    @Column(name = "password")
    private String password;

    @Column(name = "identity_issuer", length = 512)
    private String identityIssuer;

    @Column(name = "identity_subject", length = 255)
    private String identitySubject;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_status", length = 32)
    private CustomerStatus customerStatus;

    @NotNull(message = "KYC status cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false)
    private KycStatus kycStatus = KycStatus.CREATED;

    @Column(name = "kyc_verified_at")
    private LocalDateTime kycVerifiedAt;

    @Column(name = "kyc_reviewed_at")
    private LocalDateTime kycReviewedAt;

    @Column(name = "kyc_reviewed_by", length = 255)
    private String kycReviewedBy;

    @Column(name = "kyc_rejection_reason", length = 500)
    private String kycRejectionReason;

    public void addAddress(Address address) {
        addresses.add(address);
        address.setCustomer(this);
    }

    public void removeAddress(Address address) {
        if (addresses.remove(address)) {
            address.setCustomer(null);
        }
    }
}
