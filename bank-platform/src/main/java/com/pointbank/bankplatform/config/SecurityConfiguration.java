
package com.pointbank.bankplatform.config;

import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityConfiguration.class);

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Value("${app.security.jwt.roles-claim:roles}") String rolesClaim,
            @Value("${app.security.jwt.issuer-uri:}") String issuerUri,
            @Value("${app.security.jwt.audience:}") String audience
    ) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
               .authorizeHttpRequests(authorize -> {
    authorize.requestMatchers(
            "/actuator/health",
            "/actuator/health/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs",
            "/v3/api-docs/**"
    ).permitAll();

    authorize.requestMatchers(
            HttpMethod.POST,
            "/api/v1/customers"
    ).permitAll();

    if (StringUtils.hasText(issuerUri)
            && StringUtils.hasText(audience)) {
        authorize.anyRequest().authenticated();
    } else {
        authorize.anyRequest().denyAll();
    }
});

        if (StringUtils.hasText(issuerUri) && StringUtils.hasText(audience)) {
            http.oauth2ResourceServer(resourceServer -> resourceServer
                    .jwt(jwt -> jwt
                            .jwtAuthenticationConverter(jwtAuthenticationConverter(rolesClaim))));
        } else {
            LOGGER.warn(
                    "OIDC is not configured. Protected API routes are disabled; set JWT_ISSUER_URI and JWT_AUDIENCE to enable authentication."
            );
        }

        return http.build();
    }

    JwtAuthenticationConverter jwtAuthenticationConverter(
            String rolesClaim) {

        JwtGrantedAuthoritiesConverter authoritiesConverter =
                new JwtGrantedAuthoritiesConverter();

        authoritiesConverter.setAuthoritiesClaimName(rolesClaim);
        authoritiesConverter.setAuthorityPrefix("");

        Converter<Jwt, Collection<GrantedAuthority>> normalizedAuthoritiesConverter = jwt -> {
            Collection<GrantedAuthority> authorities = authoritiesConverter.convert(jwt);
            if (authorities == null) {
                return List.of();
            }
            return authorities.stream()
                    .map(GrantedAuthority::getAuthority)
                    .map(authority -> authority.startsWith("ROLE_")
                            ? authority
                            : "ROLE_" + authority)
                    .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                    .toList();
        };

        JwtAuthenticationConverter authenticationConverter =
                new JwtAuthenticationConverter();

        authenticationConverter.setJwtGrantedAuthoritiesConverter(
                normalizedAuthoritiesConverter);

        return authenticationConverter;
    }
}
