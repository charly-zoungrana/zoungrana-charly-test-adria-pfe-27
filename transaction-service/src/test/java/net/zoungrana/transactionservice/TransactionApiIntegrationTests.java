package net.zoungrana.transactionservice;

import net.zoungrana.transactionservice.domain.Transaction;
import net.zoungrana.transactionservice.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TransactionApiIntegrationTests extends FeignTestSupport {
    @LocalServerPort
    int port;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    TransactionService transactionService;

    @Test
    void restCreatesListsAndFindsTransactionsWithExactAmounts() throws Exception {
        assertThat(transactionService.getAllTransactions()).hasSizeGreaterThanOrEqualTo(3)
                .allSatisfy(transaction -> {
                    assertThat(transaction.getId()).isNotNull();
                    assertThat(transaction.getCreatedAt()).isNotNull();
                });
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> created = request(client, "/transactions", """
                    {"accountId":"11111111-1111-1111-1111-111111111111",
                     "amount":12.12345678,"currency":"MAD","merchantCategory":"REST-TEST","country":"MA"}
                    """);
            assertThat(created.statusCode()).isEqualTo(201);
            JsonNode transaction = mapper.readTree(created.body());
            String id = transaction.path("id").asText();
            assertThat(UUID.fromString(id)).isNotNull();
            assertThat(Instant.parse(transaction.path("createdAt").asText())).isNotNull();
            assertThat(transactionService.getTransactionById(UUID.fromString(id)).getAmount())
                    .isEqualByComparingTo(new BigDecimal("12.12345678"));
            HttpResponse<String> found = request(client, "/transactions/" + id, null);
            assertThat(found.statusCode()).isEqualTo(200);
            JsonNode account = mapper.readTree(found.body()).path("account");
            assertThat(account.path("id").asText()).isEqualTo(transaction.path("accountId").asText());
            assertThat(account.path("currency").asText()).isEqualTo("MAD");
            assertThat(account.path("customerReference").asText()).isEqualTo("test-account");
            assertThat(mapper.readTree(found.body()).path("id").asText()).isEqualTo(id);
            HttpResponse<String> listed = request(client, "/transactions", null);
            assertThat(listed.statusCode()).isEqualTo(200);
            assertThat(listed.body()).contains(id);
            assertThat(request(client, "/transactions/" + UUID.randomUUID(), null).statusCode()).isEqualTo(404);
        }
    }

    @Test
    void graphqlCreatesListsAndFindsTransactions() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            String mutation = """
                    mutation {
                      saveTransaction(transaction: {
                        accountId: "22222222-2222-2222-2222-222222222222",
                        amount: "19.12345678", currency: "MAD", merchantCategory: "GRAPHQL-TEST", country: "MA"
                      }) { id accountId amount currency merchantCategory country createdAt }
                    }
                    """;
            HttpResponse<String> created = request(client, "/graphql", mapper.writeValueAsString(Map.of("query", mutation)));
            assertThat(created.statusCode()).isEqualTo(200);
            JsonNode result = mapper.readTree(created.body());
            assertThat(result.has("errors")).as(created.body()).isFalse();
            JsonNode transaction = result.path("data").path("saveTransaction");
            String id = transaction.path("id").asText();
            assertThat(transaction.path("amount").asText()).isEqualTo("19.12345678");
            assertThat(Instant.parse(transaction.path("createdAt").asText())).isNotNull();
            String query = "{ getTransactionById(id: \"" + id + "\") { id amount account { id customerReference balance } } getAllTransactions { id } }";
            HttpResponse<String> found = request(client, "/graphql", mapper.writeValueAsString(Map.of("query", query)));
            JsonNode foundResult = mapper.readTree(found.body());
            assertThat(foundResult.has("errors")).as(found.body()).isFalse();
            assertThat(foundResult.path("data").path("getTransactionById").path("id").asText()).isEqualTo(id);
            assertThat(foundResult.path("data").path("getAllTransactions").toString()).contains(id);
            assertThat(foundResult.path("data").path("getTransactionById").path("account").path("id").asText())
                    .isEqualTo("22222222-2222-2222-2222-222222222222");
            assertThat(request(client, "/transactions/" + id, null).statusCode()).isEqualTo(200);
        }
    }

    @Test
    void rejectsNonPositiveOverLimitAndOverPrecisionAmounts() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            for (String amount : new String[]{"0", "-1", "1000.01", "1.123456789"}) {
                String body = "{\"accountId\":\"11111111-1111-1111-1111-111111111111\",\"amount\":"
                        + amount + ",\"currency\":\"MAD\"}";
                assertThat(request(client, "/transactions", body).statusCode()).as("amount=" + amount).isEqualTo(400);
            }
        }
    }

    private HttpResponse<String> request(HttpClient client, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (body == null) {
            builder.GET();
        } else {
            builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}