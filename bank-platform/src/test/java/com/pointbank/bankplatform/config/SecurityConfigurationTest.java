package com.pointbank.bankplatform.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class SecurityConfigurationTest {

    @Test
    void mapsConfiguredRolesToSpringRolesWithoutDoublePrefixing() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("roles", List.of("CUSTOMER", "ROLE_KYC_REVIEWER"))
                .build();

        var authentication = new SecurityConfiguration()
                .jwtAuthenticationConverter("roles")
                .convert(jwt);

        assertTrue(authentication.getAuthorities().contains(
                new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        assertTrue(authentication.getAuthorities().contains(
                new SimpleGrantedAuthority("ROLE_KYC_REVIEWER")));
    }
}
