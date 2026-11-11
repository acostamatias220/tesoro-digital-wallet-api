package com.tesoro.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.tesoro.application.model.OutboxMessage;
import com.tesoro.application.port.in.OutboxRelayUseCase;
import com.tesoro.application.port.out.EventPublisher;
import com.tesoro.support.IntegrationTest;
import com.tesoro.support.Session;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@Import(AdminIT.RecordingPublisherConfig.class)
class AdminIT extends IntegrationTest {

    @Autowired
    private OutboxRelayUseCase outboxRelay;

    @Autowired
    private RecordingPublisher publisher;

    @BeforeEach
    void resetPublisher() {
        publisher.published.clear();
        publisher.failNext = false;
    }

    @Test
    void adminEndpointsRequireTheAdminRole() {
        Session ana = register("Ana");
        ana.get("/api/v1/admin/users").expectProblem(403, "forbidden");
        ana.post("/api/v1/admin/reconciliations", null, null).expectProblem(403, "forbidden");
        api.get("/api/v1/admin/users", null).expectProblem(401, "unauthorized");

        JsonNode users = admin().get("/api/v1/admin/users").expectStatus(200).body();
        assertThat(users.get("totalElements").asInt()).isEqualTo(2);
        assertThat(users.get("content").findValuesAsText("role")).containsExactlyInAnyOrder("ADMIN", "USER");
    }

    @Test
    void adminCanFreezeAndReactivateAWalletAndItIsAudited() {
        Session ana = register("Ana");
        Session admin = admin();
        ana.deposit("ARS", "100").expectStatus(201);
        String walletId = ana.walletId("ARS");

        JsonNode frozen = api.patch(
                        "/api/v1/admin/accounts/" + walletId + "/status",
                        admin.accessToken(),
                        "{\"status\": \"FROZEN\", \"reason\": \"Revisión de fraude\"}")
                .expectStatus(200)
                .body();
        assertThat(frozen.get("status").asText()).isEqualTo("FROZEN");
        ana.withdraw("ARS", "10", newKey()).expectProblem(422, "account-frozen");

        api.patch("/api/v1/admin/accounts/" + walletId + "/status", admin.accessToken(), "{\"status\": \"ACTIVE\"}")
                .expectStatus(200);
        ana.withdraw("ARS", "10", newKey()).expectStatus(201);

        JsonNode audit = admin.get("/api/v1/admin/audit-log?action=ACCOUNT_STATUS_CHANGED")
                .expectStatus(200)
                .body();
        assertThat(audit.get("totalElements").asInt()).isEqualTo(2);
        JsonNode first = audit.get("content").get(1);
        assertThat(first.get("targetId").asText()).isEqualTo(walletId);
        assertThat(first.get("details").get("reason").asText()).isEqualTo("Revisión de fraude");

        api.patch(
                        "/api/v1/admin/accounts/00000000-0000-0000-0000-00000000a001/status",
                        admin.accessToken(),
                        "{\"status\": \"FROZEN\"}")
                .expectProblem(404, "not-found");
    }

    @Test
    void adminCanSeeAnyUsersWalletsAndTransactions() {
        Session ana = register("Ana");
        Session admin = admin();
        String transactionId = ana.deposit("USD", "5")
                .expectStatus(201)
                .body()
                .get("transactionId")
                .asText();
        String anaId = ana.get("/api/v1/me").body().get("id").asText();

        assertThat(admin.get("/api/v1/admin/users/" + anaId + "/accounts")
                        .expectStatus(200)
                        .body())
                .hasSize(2);
        admin.get("/api/v1/transactions/" + transactionId).expectStatus(200);
        admin.get("/api/v1/accounts/" + ana.walletId("USD")).expectStatus(200);
    }

    @Test
    void outboxEventsAreWrittenWithTheMovementAndRelayedOnce() {
        Session ana = register("Ana");
        Session bruno = register("Bruno");
        ana.deposit("ARS", "100").expectStatus(201);
        ana.transfer(bruno.email(), "ARS", "40", newKey()).expectStatus(201);
        ana.withdraw("ARS", "500", newKey()).expectStatus(422); // rolled back: no event

        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE published_at IS NULL", Long.class))
                .isEqualTo(4); // 2 x UserRegistered, MoneyDeposited, TransferCompleted

        assertThat(outboxRelay.publishPending()).isEqualTo(4);
        assertThat(outboxRelay.publishPending()).isZero();
        assertThat(publisher.published)
                .extracting(OutboxMessage::eventType)
                .containsExactly("UserRegistered", "UserRegistered", "MoneyDeposited", "TransferCompleted");
        assertThat(publisher.published.getLast().payload()).contains("\"currency\": \"ARS\"");

        JsonNode outbox = admin().get("/api/v1/admin/outbox").expectStatus(200).body();
        assertThat(outbox.get("content").findValues("publishedAt")).hasSize(4);
    }

    @Test
    void failedPublicationsAreRetriedLater() {
        register("Ana");
        publisher.failNext = true;

        assertThat(outboxRelay.publishPending()).isZero();
        assertThat(jdbc.queryForObject("SELECT attempts FROM outbox_events", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT last_error FROM outbox_events", String.class))
                .contains("broker down");

        assertThat(outboxRelay.publishPending()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE published_at IS NULL", Long.class))
                .isZero();
    }

    static class RecordingPublisher implements EventPublisher {
        final List<OutboxMessage> published = new CopyOnWriteArrayList<>();
        volatile boolean failNext;

        @Override
        public void publish(OutboxMessage message) {
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("broker down");
            }
            published.add(message);
        }
    }

    @TestConfiguration
    static class RecordingPublisherConfig {
        @Bean
        @Primary
        RecordingPublisher recordingPublisher() {
            return new RecordingPublisher();
        }
    }
}
