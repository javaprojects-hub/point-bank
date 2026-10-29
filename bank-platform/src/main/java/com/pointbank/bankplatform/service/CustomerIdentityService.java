package com.pointbank.bankplatform.service;

import java.util.Locale;
import java.util.UUID;

import com.pointbank.bankplatform.entity.Customer;
import com.pointbank.bankplatform.repository.CustomerRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CustomerIdentityService {

    private final CustomerRepository customerRepository;

    public CustomerIdentityService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional
    public void assertOwnsCustomerId(Long customerId, Jwt jwt) {
        Customer customer = resolveCustomer(jwt);
        if (!customer.getCustomerId().equals(customerId)) {
            throw new AccessDeniedException("Customer access is not authorized.");
        }
    }

    @Transactional
    public Long getAuthenticatedCustomerId(Jwt jwt) {
        return resolveCustomer(jwt).getCustomerId();
    }

    @Transactional
    public void assertOwnsCustomerUuid(UUID customerUuid, Jwt jwt) {
        Customer customer = resolveCustomer(jwt);
        if (!customer.getId().equals(customerUuid)) {
            throw new AccessDeniedException("Customer access is not authorized.");
        }
    }

    private Customer resolveCustomer(Jwt jwt) {
        String issuer = jwt.getIssuer() == null ? null : jwt.getIssuer().toString();
        String subject = jwt.getSubject();
        if (!StringUtils.hasText(issuer) || !StringUtils.hasText(subject)) {
            throw new AccessDeniedException("Authenticated identity is incomplete.");
        }

        Customer linkedCustomer = customerRepository
                .findByIdentityIssuerAndIdentitySubject(issuer, subject)
                .orElse(null);
        if (linkedCustomer != null) {
            return linkedCustomer;
        }

        if (!Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) {
            throw new AccessDeniedException("A verified email is required to link this account.");
        }

        String email = jwt.getClaimAsString("email");
        if (!StringUtils.hasText(email)) {
            throw new AccessDeniedException("A verified email is required to link this account.");
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        Customer customer = customerRepository.findByEmailIgnoreCaseForUpdate(normalizedEmail)
                .orElseThrow(() -> new AccessDeniedException("No customer account matches this identity."));
        if (customer.getIdentityIssuer() != null || customer.getIdentitySubject() != null) {
            throw new AccessDeniedException("Customer account is already linked to another identity.");
        }

        customer.setIdentityIssuer(issuer);
        customer.setIdentitySubject(subject);
        customer.setEmailVerified(true);
        return customerRepository.save(customer);
    }
}
