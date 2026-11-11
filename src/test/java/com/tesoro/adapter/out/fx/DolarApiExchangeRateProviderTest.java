package com.tesoro.adapter.out.fx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import com.tesoro.application.exception.ExchangeRateUnavailableException;
import com.tesoro.config.FxProperties;
import com.tesoro.domain.exchange.ExchangeQuote;
import com.tesoro.domain.model.CurrencyCode;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** Exercises the real HTTP client, cache, retry and circuit breaker against a local fake API. */
class DolarApiExchangeRateProviderTest {

    private static final String OK_BODY = """
            {"moneda":"USD","casa":"oficial","nombre":"Oficial","compra":1420.5,"venta":1470,
             "fechaActualizacion":"2026-05-01T15:00:00.000Z"}
            """;

    private HttpServer server;
    private final AtomicInteger hits = new AtomicInteger();
    private final AtomicInteger status = new AtomicInteger(200);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-05-01T15:01:00Z"));
    private DolarApiExchangeRateProvider provider;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/dolares/oficial", exchange -> {
            hits.incrementAndGet();
            byte[] body = (status.get() == 200 ? OK_BODY : "{\"error\":\"boom\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        var settings = new FxProperties.DolarApi(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "/v1/dolares/oficial",
                Duration.ofSeconds(60),
                Duration.ofMinutes(30),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1));
        var properties = new FxProperties("dolarapi", new FxProperties.Mock(BigDecimal.ONE, BigDecimal.ONE), settings);
        var breakers = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .build());
        var retries = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ofMillis(1))
                .build());
        provider = new DolarApiExchangeRateProvider(RestClient.builder(), properties, breakers, retries, clock);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void mapsTheOfficialRateToAQuote() {
        ExchangeQuote quote = provider.currentQuote(CurrencyCode.USD, CurrencyCode.ARS);

        assertThat(quote.bid()).isEqualByComparingTo("1420.5");
        assertThat(quote.ask()).isEqualByComparingTo("1470");
        assertThat(quote.source()).isEqualTo("dolarapi:oficial");
        assertThat(quote.asOf()).isEqualTo(Instant.parse("2026-05-01T15:00:00Z"));
    }

    @Test
    void cachesTheQuoteForTheConfiguredTtl() {
        provider.currentQuote(CurrencyCode.USD, CurrencyCode.ARS);
        clock.advance(Duration.ofSeconds(59));
        provider.currentQuote(CurrencyCode.USD, CurrencyCode.ARS);
        assertThat(hits).hasValue(1);

        clock.advance(Duration.ofSeconds(2));
        provider.currentQuote(CurrencyCode.USD, CurrencyCode.ARS);
        assertThat(hits).hasValue(2);
    }

    @Test
    void retriesAndFallsBackToTheLastGoodQuoteWhileItIsRecent() {
        ExchangeQuote good = provider.currentQuote(CurrencyCode.USD, CurrencyCode.ARS);
        status.set(500);
        clock.advance(Duration.ofMinutes(5));

        assertThat(provider.currentQuote(CurrencyCode.USD, CurrencyCode.ARS)).isEqualTo(good);
        assertThat(hits).hasValue(3); // 1 good + 2 attempts (retry)

        clock.advance(Duration.ofMinutes(30));
        assertThatThrownBy(() -> provider.currentQuote(CurrencyCode.USD, CurrencyCode.ARS))
                .isInstanceOf(ExchangeRateUnavailableException.class);
    }

    @Test
    void circuitOpensAfterRepeatedFailuresAndStopsCallingTheApi() {
        status.set(503);
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> provider.currentQuote(CurrencyCode.USD, CurrencyCode.ARS))
                    .isInstanceOf(ExchangeRateUnavailableException.class);
        }
        assertThat(provider.circuitState()).isEqualTo(CircuitBreaker.State.OPEN);
        int callsWhenOpened = hits.get();

        assertThatThrownBy(() -> provider.currentQuote(CurrencyCode.USD, CurrencyCode.ARS))
                .isInstanceOf(ExchangeRateUnavailableException.class);
        assertThat(hits).hasValue(callsWhenOpened);
    }

    @Test
    void onlySupportsUsdArs() {
        assertThatThrownBy(() -> provider.currentQuote(CurrencyCode.ARS, CurrencyCode.USD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now;

        MutableClock(Instant start) {
            this.now = new AtomicReference<>(start);
        }

        void advance(Duration duration) {
            now.updateAndGet(instant -> instant.plus(duration));
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
