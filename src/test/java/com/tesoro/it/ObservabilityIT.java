package com.tesoro.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.tesoro.support.IntegrationTest;
import com.tesoro.support.Session;
import org.junit.jupiter.api.Test;

class ObservabilityIT extends IntegrationTest {

    @Test
    void healthProbesArePublic() {
        JsonNode health = api.get("/actuator/health", null).expectStatus(200).body();
        assertThat(health.get("status").asText()).isEqualTo("UP");
        api.get("/actuator/health/readiness", null).expectStatus(200);
        api.get("/actuator/health/liveness", null).expectStatus(200);
    }

    @Test
    void prometheusExposesBusinessMetrics() {
        Session ana = register("Ana");
        ana.deposit("USD", "1").expectStatus(201);

        String metrics = api.get("/actuator/prometheus", null).expectStatus(200).text();
        assertThat(metrics)
                .contains("tesoro_movements_total{application=\"tesoro\",currency=\"USD\",type=\"DEPOSIT\"}")
                .contains("tesoro_reconciliation_issues")
                .contains("tesoro_outbox_pending")
                .contains("http_server_requests_seconds");
    }

    @Test
    void otherActuatorEndpointsAreAdminOnly() {
        api.get("/actuator/metrics", null).expectStatus(401);
        register("Ana").get("/actuator/metrics").expectStatus(403);
        admin().get("/actuator/metrics").expectStatus(200);
    }

    @Test
    void openApiDocumentDescribesTheApi() {
        JsonNode doc = api.get("/v3/api-docs", null).expectStatus(200).body();
        assertThat(doc.get("info").get("title").asText()).isEqualTo("Tesoro API");
        assertThat(doc.get("paths").has("/api/v1/transfers")).isTrue();
        assertThat(doc.get("components").get("securitySchemes").has("bearer")).isTrue();
        api.get("/swagger-ui/index.html", null).expectStatus(200);
    }

    @Test
    void unknownRoutesAndMalformedBodiesReturnProblemDetails() {
        Session ana = register("Ana");
        assertThat(ana.post("/api/v1/deposits", newKey(), "{ not json").status())
                .isEqualTo(400);
        assertThat(ana.post("/api/v1/deposits", newKey(), "{ not json").header("Content-Type"))
                .startsWith("application/problem+json");
    }
}
