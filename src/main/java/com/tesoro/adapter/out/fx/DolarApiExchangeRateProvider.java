package com.tesoro.adapter.out.fx;

import com.tesoro.application.exception.ExchangeRateUnavailableException;
import com.tesoro.application.port.out.ExchangeRateProvider;
import com.tesoro.config.FxProperties;
import com.tesoro.domain.exchange.ExchangeQuote;
import com.tesoro.domain.model.CurrencyCode;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Instant;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Official USD/ARS rate from <a href="https://dolarapi.com">dolarapi.com</a>.
 *
 * <ul>
 *   <li><b>Cache</b>: a quote is reused for {@code cache-ttl} to avoid calling the API on every
 *       conversion.
 *   <li><b>Retry + circuit breaker</b> (Resilience4j): transient errors are retried once; if the API
 *       keeps failing the breaker opens and calls fail fast instead of piling up.
 *   <li><b>Fallback</b>: while the API is down, the last good quote is used for up to {@code
 *       max-staleness}; after that conversions are rejected with 503 rather than using a stale rate.
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "tesoro.fx.provider", havingValue = "dolarapi")
class DolarApiExchangeRateProvider implements ExchangeRateProvider {

    static final String RESILIENCE_INSTANCE = "dolarApi";
    private static final Logger log = LoggerFactory.getLogger(DolarApiExchangeRateProvider.class);

    private final RestClient restClient;
    private final FxProperties.DolarApi settings;
    private final Supplier<ExchangeQuote> resilientFetch;
    private final CircuitBreaker circuitBreaker;
    private final Clock clock;

    private volatile CachedQuote cached;

    DolarApiExchangeRateProvider(
            RestClient.Builder builder,
            FxProperties properties,
            CircuitBreakerRegistry circuitBreakers,
            RetryRegistry retries,
            Clock clock) {
        this.settings = properties.dolarapi();
        this.clock = clock;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(settings.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(settings.readTimeout());
        this.restClient = builder.clone()
                .baseUrl(settings.baseUrl())
                .requestFactory(requestFactory)
                .build();
        this.circuitBreaker = circuitBreakers.circuitBreaker(RESILIENCE_INSTANCE);
        Retry retry = retries.retry(RESILIENCE_INSTANCE);
        this.resilientFetch =
                Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(circuitBreaker, this::fetch));
    }

    @Override
    public ExchangeQuote currentQuote(CurrencyCode base, CurrencyCode quote) {
        UsdArs.require(base, quote);
        Instant now = clock.instant();
        CachedQuote current = cached;
        if (current != null && current.fetchedAt().plus(settings.cacheTtl()).isAfter(now)) {
            return current.quote();
        }
        try {
            ExchangeQuote fresh = resilientFetch.get();
            cached = new CachedQuote(fresh, now);
            return fresh;
        } catch (RuntimeException e) {
            if (current != null
                    && !current.fetchedAt().plus(settings.maxStaleness()).isBefore(now)) {
                log.warn(
                        "dolarapi unavailable ({}), using quote fetched at {} (circuit breaker {})",
                        e.toString(),
                        current.fetchedAt(),
                        circuitBreaker.getState());
                return current.quote();
            }
            log.error("dolarapi unavailable and no recent quote to fall back on", e);
            throw new ExchangeRateUnavailableException(
                    "La cotización no está disponible en este momento. Probá de nuevo en unos minutos");
        }
    }

    private ExchangeQuote fetch() {
        DolarApiResponse body = restClient.get().uri(settings.path()).retrieve().body(DolarApiResponse.class);
        if (body == null || body.compra() == null || body.venta() == null) {
            throw new IllegalStateException("Unexpected dolarapi response: " + body);
        }
        Instant asOf = body.fechaActualizacion() != null ? body.fechaActualizacion() : clock.instant();
        return new ExchangeQuote(
                CurrencyCode.USD, CurrencyCode.ARS, body.compra(), body.venta(), "dolarapi:" + body.casa(), asOf);
    }

    CircuitBreaker.State circuitState() {
        return circuitBreaker.getState();
    }

    record DolarApiResponse(
            String moneda, String casa, BigDecimal compra, BigDecimal venta, Instant fechaActualizacion) {}

    private record CachedQuote(ExchangeQuote quote, Instant fetchedAt) {}
}
