package com.pointbank.bankplatform.config;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class JwtConfigurationTest {

    @Test
    void doesNotCreateJwtDecoderWhenOidcIsNotConfigured() {
        new ApplicationContextRunner()
                .withUserConfiguration(JwtConfiguration.class)
                .run(context -> assertFalse(context.getBeanFactory().containsBean("jwtDecoder")));
    }
}
