package com.tesoro.config;

import com.tesoro.application.service.UserProvisioningService;
import com.tesoro.domain.model.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/** Creates the first administrator from ADMIN_EMAIL / ADMIN_PASSWORD (if both are set). */
@Configuration(proxyBeanMethods = false)
class BootstrapConfig {

    private static final Logger log = LoggerFactory.getLogger(BootstrapConfig.class);

    @Bean
    @Order(1)
    ApplicationRunner bootstrapAdmin(
            UserProvisioningService provisioning,
            @Value("${tesoro.admin.email:}") String email,
            @Value("${tesoro.admin.password:}") String password) {
        return args -> {
            if (email.isBlank() || password.isBlank()) {
                log.info("ADMIN_EMAIL / ADMIN_PASSWORD not set: no administrator is bootstrapped");
                return;
            }
            if (password.length() < 12) {
                throw new IllegalStateException("ADMIN_PASSWORD must have at least 12 characters");
            }
            if (provisioning.ensureUser(email, password, "Administrador", Role.ADMIN)) {
                log.info("Administrator {} created", email);
            }
        };
    }
}
