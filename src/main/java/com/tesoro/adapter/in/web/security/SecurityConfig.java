package com.tesoro.adapter.in.web.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * Stateless API secured with Bearer JWTs. CSRF protection is disabled on purpose: there are no
 * cookies or sessions, so a browser cannot be tricked into sending credentials automatically.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(
            HttpSecurity http, ProblemSecurityHandlers problemHandlers, RateLimitFilter rateLimitFilter)
            throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout")
                        .permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus")
                        .permitAll()
                        .requestMatchers("/error")
                        .permitAll()
                        .requestMatchers("/api/v1/admin/**", "/actuator/**")
                        .hasRole("ADMIN")
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(
                        oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                                .authenticationEntryPoint(problemHandlers)
                                .accessDeniedHandler(problemHandlers))
                .exceptionHandling(
                        e -> e.authenticationEntryPoint(problemHandlers).accessDeniedHandler(problemHandlers))
                .headers(headers -> headers.frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER)))
                .addFilterAfter(rateLimitFilter, BearerTokenAuthenticationFilter.class)
                .build();
    }

    /** The filter runs inside the security chain only (after the JWT is validated), not twice. */
    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /** Maps the {@code roles} claim (e.g. ["ADMIN"]) to Spring authorities (ROLE_ADMIN). */
    static Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
