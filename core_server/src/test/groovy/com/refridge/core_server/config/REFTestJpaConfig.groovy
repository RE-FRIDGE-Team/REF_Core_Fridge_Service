package com.refridge.core_server.config

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.data.domain.AuditorAware
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

@TestConfiguration
@EnableJpaAuditing
class REFTestJpaConfig {

    @Bean
    AuditorAware<String> auditorProvider() {
        return { Optional.of("test-user") }
    }
}