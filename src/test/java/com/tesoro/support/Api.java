package com.tesoro.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Minimal JSON-over-HTTP client for the tests (never throws on 4xx/5xx). */
public final class Api {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));
    private static final HttpClient CLIENT =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private final String baseUrl;

    Api(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Response get(String path, String token) {
        return send("GET", path, token, null, null);
    }

    public Response post(String path, String token, String idempotencyKey, String body) {
        return send("POST", path, token, idempotencyKey, body);
    }

    public Response patch(String path, String token, String body) {
        return send("PATCH", path, token, null, body);
    }

    public Response send(String method, String path, String token, String idempotencyKey, String body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .method(
                        method,
                        body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        try {
            HttpResponse<String> response = CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    public static final class Response {
        private final HttpResponse<String> raw;

        Response(HttpResponse<String> raw) {
            this.raw = raw;
        }

        public int status() {
            return raw.statusCode();
        }

        public String header(String name) {
            return raw.headers().firstValue(name).orElse(null);
        }

        public String text() {
            return raw.body();
        }

        public JsonNode body() {
            try {
                return raw.body() == null || raw.body().isBlank()
                        ? MissingNode.getInstance()
                        : MAPPER.readTree(raw.body());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        public Response expectStatus(int expected) {
            assertThat(status()).as("HTTP status, body: %s", raw.body()).isEqualTo(expected);
            return this;
        }

        /** Asserts an RFC 9457 problem with the given status and code. */
        public JsonNode expectProblem(int expected, String code) {
            expectStatus(expected);
            assertThat(header("Content-Type")).startsWith("application/problem+json");
            JsonNode body = body();
            assertThat(body.get("code").asText()).isEqualTo(code);
            assertThat(body.get("type").asText()).isEqualTo("urn:tesoro:problem:" + code);
            assertThat(body.get("status").asInt()).isEqualTo(expected);
            assertThat(body.hasNonNull("detail")).isTrue();
            return body;
        }
    }
}
