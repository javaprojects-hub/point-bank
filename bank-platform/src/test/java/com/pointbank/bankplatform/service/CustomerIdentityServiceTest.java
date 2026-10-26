package com.pointbank.bankplatform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.pointbank.bankplatform.entity.Customer;
import com.pointbank.bankplatform.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class CustomerIdentityServiceTest {

    private static final String ISSUER = "https://identity.example.test";
    private static final String SUBJECT = "customer-123";
    private static final String EMAIL = "asha@example.com";

    @Mock
    private CustomerRepository customerRepository;

    private CustomerIdentityService customerIdentityService;

    @BeforeEach
    void setUp() {
        customerIdentityService = new CustomerIdentityService(customerRepository);
    }

    @Test
    void linksVerifiedEmailToStableIssuerAndSubjectOnFirstUse() {
        Customer customer = new Customer();
        customer.setCustomerId(123L);
        customer.setEmail(EMAIL);
        when(customerRepository.findByIdentityIssuerAndIdentitySubject(ISSUER, SUBJECT))
                .thenReturn(Optional.empty());
        when(customerRepository.findByEmailIgnoreCaseForUpdate(EMAIL))
                .thenReturn(Optional.of(customer));
        when(customerRepository.save(customer)).thenReturn(customer);

        customerIdentityService.assertOwnsCustomerId(123L, jwt(true));

        assertEquals(ISSUER, customer.getIdentityIssuer());
        assertEquals(SUBJECT, customer.getIdentitySubject());
        verify(customerRepository).save(customer);
    }

    @Test
    void reusesExistingStableIdentityWithoutDependingOnEmailClaim() {
        Customer customer = new Customer();
        customer.setCustomerId(123L);
        customer.setId(UUID.randomUUID());
        customer.setIdentityIssuer(ISSUER);
        customer.setIdentitySubject(SUBJECT);
        when(customerRepository.findByIdentityIssuerAndIdentitySubject(ISSUER, SUBJECT))
                .thenReturn(Optional.of(customer));

        customerIdentityService.assertOwnsCustomerId(123L, jwt(false));
    }

    @Test
    void deniesFirstLinkWhenEmailIsNotVerified() {
        when(customerRepository.findByIdentityIssuerAndIdentitySubject(ISSUER, SUBJECT))
                .thenReturn(Optional.empty());

        assertThrows(
                AccessDeniedException.class,
                () -> customerIdentityService.assertOwnsCustomerId(123L, jwt(false))
        );
    }

    @Test
    void deniesAccessWhenIdentityIsLinkedToAnotherCustomer() {
        Customer customer = new Customer();
        customer.setCustomerId(123L);
        customer.setIdentityIssuer(ISSUER);
        customer.setIdentitySubject(SUBJECT);
        when(customerRepository.findByIdentityIssuerAndIdentitySubject(ISSUER, SUBJECT))
                .thenReturn(Optional.of(customer));

        assertThrows(
                AccessDeniedException.class,
                () -> customerIdentityService.assertOwnsCustomerId(456L, jwt(false))
        );
    }

    private Jwt jwt(boolean emailVerified) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .issuer(ISSUER)
                .subject(SUBJECT)
                .claim("email", EMAIL)
                .claim("email_verified", emailVerified)
                .build();
    }
}
