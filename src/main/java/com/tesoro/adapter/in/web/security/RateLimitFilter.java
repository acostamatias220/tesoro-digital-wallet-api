package com.tesoro.adapter.in.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tesoro.adapter.in.web.error.Problems;
import com.tesoro.config.RateLimitProperties;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Token-bucket rate limiting with Bucket4j:
 *
 * <ul>
 *   <li>auth endpoints: per client IP (slows down credential stuffing),
 *   <li>money-moving POSTs: per user,
 *   <li>everything else under /api: per user (or IP when anonymous).
 * </ul>
 *
 * Buckets live in memory (Caffeine, evicted when idle). With several instances, the same
 * buckets would move to Redis through bucket4j's distributed backends.
 */
@Component
class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(100_000)
            .build();

    RateLimitFilter(RateLimitProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled() || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String caller = callerId(request);
        String key;
        int perMinute;
        if (path.startsWith("/api/v1/auth/")) {
            key = "auth:" + request.getRemoteAddr();
            perMinute = properties.authPerMinute();
        } else if ("POST".equals(request.getMethod()) && isMoneyEndpoint(path)) {
            key = "ops:" + caller;
            perMinute = properties.operationsPerMinute();
        } else {
            key = "api:" + caller;
            perMinute = properties.defaultPerMinute();
        }

        Bucket bucket = buckets.get(key + ":" + perMinute, k -> newBucket(perMinute));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        response.setHeader("X-RateLimit-Limit", String.valueOf(perMinute));
        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(request, response);
            return;
        }
        long retryAfter = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
        response.setHeader("Retry-After", String.valueOf(retryAfter));
        response.setHeader("X-RateLimit-Remaining", "0");
        ProblemDetail problem = Problems.of(
                HttpStatus.TOO_MANY_REQUESTS,
                "rate-limited",
                "Demasiadas solicitudes",
                "Superaste el límite de %d solicitudes por minuto. Reintentá en %d s".formatted(perMinute, retryAfter),
                request);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    private static boolean isMoneyEndpoint(String path) {
        return path.equals("/api/v1/deposits")
                || path.equals("/api/v1/withdrawals")
                || path.equals("/api/v1/transfers")
                || path.equals("/api/v1/conversions");
    }

    private static String callerId(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            return "user:" + jwt.getName();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private static Bucket newBucket(int perMinute) {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1)))
                .build();
    }
}
