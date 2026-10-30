package net.zoungrana.transactionservice.feign;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import io.github.resilience4j.spring6.circuitbreaker.configure.CircuitBreakerAspect;
import io.github.resilience4j.spring6.circuitbreaker.configure.CircuitBreakerConfigurationProperties;
import io.github.resilience4j.spring6.retry.configure.RetryAspect;
import io.github.resilience4j.spring6.retry.configure.RetryConfigurationProperties;
import io.github.resilience4j.spring6.fallback.FallbackExecutor;
import io.github.resilience4j.spring6.fallback.configure.FallbackConfiguration;
import io.github.resilience4j.spring6.spelresolver.SpelResolver;
import io.github.resilience4j.spring6.spelresolver.configure.SpelResolverConfiguration;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = false)
@Import({FallbackConfiguration.class, SpelResolverConfiguration.class})
public class AccountCommunicationConfig {
    @Bean
    CircuitBreakerRegistry accountCircuitBreakerRegistry(
            @Value("${bank.account-service.failure-threshold:50}") float threshold,
            @Value("${bank.account-service.open-duration-seconds:10}") long openSeconds) {
        return CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(5).minimumNumberOfCalls(3).failureRateThreshold(threshold)
                .waitDurationInOpenState(Duration.ofSeconds(openSeconds))
                .permittedNumberOfCallsInHalfOpenState(2)
                .ignoreException(error -> error instanceof FeignException ex && ex.status() >= 400 && ex.status() < 500)
                .build());
    }

    @Bean
    RetryRegistry accountRetryRegistry() {
        return RetryRegistry.of(RetryConfig.custom().maxAttempts(2)
                .waitDuration(Duration.ofMillis(200))
                .retryOnException(error -> error instanceof FeignException ex && (ex.status() < 0 || ex.status() >= 500))
                .build());
    }

    // Register the annotation aspects explicitly: this project uses Boot 4, not Boot 3 auto-configuration.
    @Bean
    CircuitBreakerAspect accountCircuitBreakerAspect(CircuitBreakerRegistry registry,
            FallbackExecutor fallbackExecutor, SpelResolver spelResolver) {
        CircuitBreakerConfigurationProperties properties = new CircuitBreakerConfigurationProperties();
        // CircuitBreaker wraps Retry, so one logical read (up to two HTTP attempts) counts once.
        properties.setCircuitBreakerAspectOrder(100);
        return new CircuitBreakerAspect(properties, registry, List.of(), fallbackExecutor, spelResolver);
    }

    @Bean
    RetryAspect accountRetryAspect(RetryRegistry registry, FallbackExecutor fallbackExecutor, SpelResolver spelResolver) {
        RetryConfigurationProperties properties = new RetryConfigurationProperties();
        properties.setRetryAspectOrder(101);
        return new RetryAspect(properties, registry, List.of(), fallbackExecutor, spelResolver, null);
    }}