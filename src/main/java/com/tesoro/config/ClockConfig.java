package com.tesoro.config;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ClockConfig {

    /**
     * All timestamps come from this UTC clock, truncated to microseconds (PostgreSQL's precision) so
     * a value returned by the API is identical to the one stored.
     */
    @Bean
    Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
    }
}
