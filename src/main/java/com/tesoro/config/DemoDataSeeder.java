package com.tesoro.config;

import com.tesoro.application.command.ConvertCommand;
import com.tesoro.application.command.DepositCommand;
import com.tesoro.application.command.TransferCommand;
import com.tesoro.application.command.WithdrawCommand;
import com.tesoro.application.port.in.ReconciliationUseCase;
import com.tesoro.application.port.in.WalletOperationsUseCase;
import com.tesoro.application.port.out.UserRepository;
import com.tesoro.application.service.UserProvisioningService;
import com.tesoro.domain.model.CurrencyCode;
import com.tesoro.domain.model.Money;
import com.tesoro.domain.model.Role;
import com.tesoro.domain.model.User;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Optional demo data (SEED_DEMO_DATA=true): two users with a few movements of each kind. The demo
 * password comes from SEED_PASSWORD; if it is empty a random one is generated and logged once.
 */
@Component
@Order(2)
@ConditionalOnProperty(name = "tesoro.seed.enabled", havingValue = "true")
class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    static final String ANA = "ana@tesoro.dev";
    static final String BRUNO = "bruno@tesoro.dev";

    private final UserProvisioningService provisioning;
    private final UserRepository users;
    private final WalletOperationsUseCase operations;
    private final ReconciliationUseCase reconciliation;
    private final String configuredPassword;

    DemoDataSeeder(
            UserProvisioningService provisioning,
            UserRepository users,
            WalletOperationsUseCase operations,
            ReconciliationUseCase reconciliation,
            @Value("${tesoro.seed.password:}") String configuredPassword) {
        this.provisioning = provisioning;
        this.users = users;
        this.operations = operations;
        this.reconciliation = reconciliation;
        this.configuredPassword = configuredPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.existsByEmail(ANA)) {
            log.info("Demo data already present");
            return;
        }
        String password = configuredPassword.isBlank() ? randomPassword() : configuredPassword;
        provisioning.ensureUser(ANA, password, "Ana Gómez", Role.USER);
        provisioning.ensureUser(BRUNO, password, "Bruno Díaz", Role.USER);
        UUID ana = users.findByEmail(ANA).map(User::id).orElseThrow();
        UUID bruno = users.findByEmail(BRUNO).map(User::id).orElseThrow();

        operations.deposit(new DepositCommand(ana, "seed-ana-deposit-ars", Money.of("850000", CurrencyCode.ARS)));
        operations.deposit(new DepositCommand(ana, "seed-ana-deposit-usd", Money.of("1200", CurrencyCode.USD)));
        operations.deposit(new DepositCommand(bruno, "seed-bruno-deposit-ars", Money.of("320000", CurrencyCode.ARS)));
        operations.deposit(new DepositCommand(bruno, "seed-bruno-deposit-usd", Money.of("300", CurrencyCode.USD)));
        operations.transfer(new TransferCommand(
                ana, "seed-ana-transfer-1", BRUNO, Money.of("45000", CurrencyCode.ARS), "Alquiler cochera"));
        operations.transfer(new TransferCommand(
                bruno, "seed-bruno-transfer-1", ANA, Money.of("12500.50", CurrencyCode.ARS), "Pizza del viernes"));
        operations.convert(
                new ConvertCommand(ana, "seed-ana-convert-1", Money.of("145000", CurrencyCode.ARS), CurrencyCode.USD));
        operations.convert(
                new ConvertCommand(bruno, "seed-bruno-convert-1", Money.of("50", CurrencyCode.USD), CurrencyCode.ARS));
        operations.withdraw(new WithdrawCommand(ana, "seed-ana-withdraw-1", Money.of("30000", CurrencyCode.ARS)));
        reconciliation.run("SEED");

        if (configuredPassword.isBlank()) {
            log.warn("Demo users {} and {} created with generated password: {}", ANA, BRUNO, password);
        } else {
            log.info("Demo users {} and {} created (password from SEED_PASSWORD)", ANA, BRUNO);
        }
    }

    private static String randomPassword() {
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
