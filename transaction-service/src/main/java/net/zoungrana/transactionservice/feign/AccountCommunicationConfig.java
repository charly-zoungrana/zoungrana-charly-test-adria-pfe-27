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

@Configuration
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
}