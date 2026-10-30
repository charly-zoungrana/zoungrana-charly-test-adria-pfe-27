package net.zoungrana.transactionservice;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

abstract class FeignTestSupport {
    static final JsonMapper JSON = new JsonMapper();
    static final Map<UUID, BigDecimal> BALANCES = new ConcurrentHashMap<>();
    static final Map<UUID, String> STATUSES = new ConcurrentHashMap<>();
    static final Map<UUID, String> CURRENCIES = new ConcurrentHashMap<>();
    static final Set<UUID> MISSING = ConcurrentHashMap.newKeySet();
    static final Set<UUID> UNAVAILABLE = ConcurrentHashMap.newKeySet();
    static final Set<UUID> SLOW = ConcurrentHashMap.newKeySet();
    static final Set<UUID> LOST_REPLY = ConcurrentHashMap.newKeySet();
    static final Set<UUID> REJECT_DEBIT = ConcurrentHashMap.newKeySet();
    static final Set<UUID> OPERATIONS = ConcurrentHashMap.newKeySet();
    static final Map<UUID, AtomicInteger> GET_CALLS = new ConcurrentHashMap<>();
    static final HttpServer SERVER = startServer();

    @DynamicPropertySource
    static void feignProperties(DynamicPropertyRegistry properties) {
        properties.add("bank.account-service.url", () -> "http://localhost:" + SERVER.getAddress().getPort());
        properties.add("spring.cloud.openfeign.client.config.account-service.connectTimeout", () -> 200);
        properties.add("spring.cloud.openfeign.client.config.account-service.readTimeout", () -> 300);
    }

    private static HttpServer startServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable);
                thread.setDaemon(true);
                return thread;
            }));
            server.createContext("/accounts", FeignTestSupport::handle);
            server.start();
            return server;
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private static Map<String, Object> account(UUID id) {
        return Map.of("id", id, "customerReference", "test-account", "status", STATUSES.getOrDefault(id, "ACTIVE"),
                "currency", CURRENCIES.getOrDefault(id, "MAD"), "balance", BALANCES.getOrDefault(id, new BigDecimal("10000")));
    }

    private static void handle(HttpExchange exchange) throws IOException {
        try {
            String[] path = exchange.getRequestURI().getPath().split("/");
            if (path.length == 2) {
                reply(exchange, 200, List.of(account(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                        account(UUID.fromString("22222222-2222-2222-2222-222222222222"))));
                return;
            }
            UUID id = UUID.fromString(path[2]);
            if ("GET".equals(exchange.getRequestMethod())) {
                GET_CALLS.computeIfAbsent(id, ignored -> new AtomicInteger()).incrementAndGet();
                if (SLOW.contains(id)) {
                    try { Thread.sleep(800); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                }
                if (MISSING.contains(id)) { reply(exchange, 404, Map.of()); return; }
                if (UNAVAILABLE.contains(id)) { reply(exchange, 503, Map.of()); return; }
                reply(exchange, 200, account(id));
            } else {
                if (REJECT_DEBIT.contains(id)) { reply(exchange, 409, Map.of()); return; }
                var body = JSON.readTree(exchange.getRequestBody().readAllBytes());
                UUID operationId = UUID.fromString(body.path("operationId").asText());
                BigDecimal amount = new BigDecimal(body.path("amount").asText());
                synchronized (OPERATIONS) {
                    if (OPERATIONS.add(operationId)) {
                        BALANCES.put(id, BALANCES.getOrDefault(id, new BigDecimal("10000")).subtract(amount));
                    }
                }
                // Simulate a debit committed remotely, followed by a lost HTTP response.
                if (LOST_REPLY.remove(id)) { exchange.close(); return; }
                reply(exchange, 200, account(id));
            }
        } finally {
            exchange.close();
        }
    }

    private static void reply(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] body = JSON.writeValueAsBytes(value);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }
}