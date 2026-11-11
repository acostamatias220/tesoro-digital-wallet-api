package com.tesoro.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.tesoro.application.model.ReconciliationReport;
import com.tesoro.application.port.in.ReconciliationUseCase;
import com.tesoro.support.Api;
import com.tesoro.support.IntegrationTest;
import com.tesoro.support.Session;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Fires real concurrent HTTP requests at the API. Every test ends with a ledger reconciliation to
 * prove the invariants still hold.
 */
class ConcurrencyIT extends IntegrationTest {

    private final ExecutorService pool = Executors.newFixedThreadPool(24);

    @Autowired
    private ReconciliationUseCase reconciliation;

    @AfterEach
    void shutdown() {
        pool.shutdownNow();
    }

    @Test
    void parallelWithdrawalsNeverSpendMoreThanTheBalance() throws Exception {
        Session ana = register("Ana");
        ana.deposit("ARS", "100").expectStatus(201);

        // 40 requests of 10 ARS each against a balance of 100: exactly 10 may succeed.
        List<Integer> statuses =
                runConcurrently(40, i -> ana.withdraw("ARS", "10", newKey()).status());

        Map<Integer, Long> byStatus = countByStatus(statuses);
        assertThat(byStatus).containsEntry(201, 10L).containsEntry(422, 30L).hasSize(2);
        assertThat(ana.balance("ARS")).isZero();
        assertLedgerIsConsistent();
    }

    @Test
    void opposingTransfersDoNotDeadlockAndConserveMoney() throws Exception {
        Session ana = register("Ana");
        Session bruno = register("Bruno");
        ana.deposit("USD", "1000").expectStatus(201);
        bruno.deposit("USD", "1000").expectStatus(201);

        // A->B and B->A at the same time: without a global lock order this deadlocks.
        List<Integer> statuses = runConcurrently(
                60,
                i -> (i % 2 == 0)
                        ? ana.transfer(bruno.email(), "USD", "7.25", newKey()).status()
                        : bruno.transfer(ana.email(), "USD", "3.10", newKey()).status());

        assertThat(statuses).containsOnly(201);
        BigDecimal expectedAna = new BigDecimal("1000")
                .subtract(new BigDecimal("7.25").multiply(BigDecimal.valueOf(30)))
                .add(new BigDecimal("3.10").multiply(BigDecimal.valueOf(30)));
        assertThat(ana.balance("USD")).isEqualByComparingTo(expectedAna);
        assertThat(ana.balance("USD").add(bruno.balance("USD"))).isEqualByComparingTo("2000");
        assertLedgerIsConsistent();
    }

    @Test
    void theSameIdempotencyKeySentConcurrentlyMovesMoneyOnce() throws Exception {
        Session ana = register("Ana");
        Session bruno = register("Bruno");
        ana.deposit("ARS", "1000").expectStatus(201);
        String key = newKey();

        List<Api.Response> responses = runConcurrently(20, i -> ana.transfer(bruno.email(), "ARS", "100", key));

        assertThat(responses).extracting(Api.Response::status).containsOnly(201);
        assertThat(responses)
                .extracting(r -> r.body().get("transactionId").asText())
                .containsOnly(responses.getFirst().body().get("transactionId").asText());
        assertThat(responses)
                .filteredOn(r -> "false".equals(r.header("Idempotent-Replayed")))
                .hasSize(1);
        assertThat(ana.balance("ARS")).isEqualByComparingTo("900");
        assertThat(bruno.balance("ARS")).isEqualByComparingTo("100");
        assertLedgerIsConsistent();
    }

    @Test
    void manyUsersDepositingAndConvertingAtOnceKeepTheLedgerBalanced() throws Exception {
        List<Session> users = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Session user = register("User" + i);
            user.deposit("ARS", "50000").expectStatus(201);
            users.add(user);
        }

        List<Integer> statuses = runConcurrently(48, i -> {
            Session user = users.get(i % users.size());
            return switch (i % 3) {
                case 0 -> user.convert("ARS", "USD", "1450", newKey()).status();
                case 1 -> user.deposit("ARS", "10.10").status();
                default ->
                    user.transfer(users.get((i + 1) % users.size()).email(), "ARS", "99.99", newKey())
                            .status();
            };
        });

        assertThat(statuses).containsOnly(201);
        assertLedgerIsConsistent();
    }

    private void assertLedgerIsConsistent() {
        ReconciliationReport report = reconciliation.run("TEST");
        assertThat(report.issues()).isEmpty();
        assertThat(report.status()).isEqualTo(ReconciliationReport.Status.OK);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM accounts WHERE kind = 'USER_WALLET' AND balance < 0", Long.class))
                .isZero();
    }

    private <T> List<T> runConcurrently(int count, Function<Integer, T> task) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int index = i;
            Callable<T> callable = () -> {
                start.await();
                return task.apply(index);
            };
            futures.add(pool.submit(callable));
        }
        start.countDown();
        List<T> results = new ArrayList<>();
        for (Future<T> future : futures) {
            results.add(future.get(60, TimeUnit.SECONDS));
        }
        return results;
    }

    private static Map<Integer, Long> countByStatus(List<Integer> statuses) {
        return statuses.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }
}
