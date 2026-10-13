package com.ledger.infrastructure.security;

import com.ledger.infrastructure.idempotency.IdempotencyFilter;
import com.ledger.infrastructure.config.LedgerSecurityProperties;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                             JwtAuthenticationFilter jwtAuthenticationFilter,
                                             IdempotencyFilter idempotencyFilter,
                                             RestAuthenticationEntryPoint entryPoint,
                                             RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .anonymous(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/token").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(idempotencyFilter, AuthorizationFilter.class)
                .build();
    }

    @Bean
    FilterRegistrationBean<JwtAuthenticationFilter> jwtRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<IdempotencyFilter> idempotencyRegistration(IdempotencyFilter filter) {
        FilterRegistrationBean<IdempotencyFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    ApplicationRunner securityPropertiesCheck(LedgerSecurityProperties properties) {
        return args -> {
            if (properties.jwtSecret() == null || properties.jwtSecret().length() < 32) {
                throw new IllegalStateException("ledger.security.jwt-secret must be at least 32 characters");
            }
            if (properties.clientId() == null || properties.clientId().isBlank()
                    || properties.clientSecret() == null || properties.clientSecret().isBlank()) {
                throw new IllegalStateException("ledger.security client id and secret are required");
            }
            if (properties.ttlMinutes() <= 0) {
                throw new IllegalStateException("ledger.security.ttl-minutes must be positive");
            }
        };
    }
}
