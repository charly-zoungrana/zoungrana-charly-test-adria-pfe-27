package net.zoungrana.gatewayservice;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.cloud.config.enabled=false", "BANK_CONFIG_IMPORT=optional:classpath:/disabled-config.properties", "eureka.client.enabled=false"})
class GatewayRoutingIntegrationTests {
    static final HttpServer ACCOUNT = server("account");
    static final HttpServer TRANSACTION = server("transaction");
    @LocalServerPort int port;

    @DynamicPropertySource
    static void instances(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.account-service[0].uri",
                () -> "http://localhost:" + ACCOUNT.getAddress().getPort());
        registry.add("spring.cloud.discovery.client.simple.instances.transaction-service[0].uri",
                () -> "http://localhost:" + TRANSACTION.getAddress().getPort());
    }

    @Test
    void forwardsRestRequestsThroughLoadBalancer() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            assertRoute(client, "/accounts", "account", "/accounts", null);
            assertRoute(client, "/accounts/test-id", "account", "/accounts/test-id", null);
            assertRoute(client, "/transactions", "transaction", "/transactions", null);
        }
    }

    @Test
    void removesServicePrefixForGraphqlAndMcpAndPreservesBodyAndHeaders() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            assertRoute(client, "/account-service/graphql", "account", "/graphql", "graphql-query");
            assertRoute(client, "/transaction-service/graphql", "transaction", "/graphql", "graphql-query");
            assertRoute(client, "/account-service/mcp", "account", "/mcp", "mcp-request");
            assertRoute(client, "/transaction-service/mcp", "transaction", "/mcp", "mcp-request");
        }
    }

    private void assertRoute(HttpClient client, String path, String service, String forwardedPath, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Mcp-Session-Id", "test-session");
        if (body != null) builder.POST(HttpRequest.BodyPublishers.ofString(body)); else builder.GET();
        var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.body()).contains(service + ":" + forwardedPath, "test-session");
        if (body != null) assertThat(response.body()).contains(body);
    }

    private static HttpServer server(String name) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                String body = name + ":" + exchange.getRequestURI().getPath() + ":"
                        + exchange.getRequestHeaders().getFirst("Mcp-Session-Id") + ":"
                        + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }
}