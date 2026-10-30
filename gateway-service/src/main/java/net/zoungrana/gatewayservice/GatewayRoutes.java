package net.zoungrana.gatewayservice;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRoutes {
    @Bean
    RouteLocator bankRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("accounts-rest", route -> route.path("/accounts", "/accounts/**")
                        .uri("lb://account-service"))
                .route("transactions-rest", route -> route.path("/transactions", "/transactions/**")
                        .uri("lb://transaction-service"))
                .route("account-connectors", route -> route.path("/account-service/**")
                        .filters(filters -> filters.stripPrefix(1)).uri("lb://account-service"))
                .route("transaction-connectors", route -> route.path("/transaction-service/**")
                        .filters(filters -> filters.stripPrefix(1)).uri("lb://transaction-service"))
                .build();
    }
}