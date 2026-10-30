package net.zoungrana.transactionservice;

import net.zoungrana.transactionservice.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TransactionMcpIntegrationTests extends FeignTestSupport {
    @LocalServerPort
    int port;
    @Autowired
    TransactionService transactionService;

    @Test
    void exposesAndInvokesAllThreeToolsOverHttp() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> initialized = send(client, null, """
                    {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
                    "protocolVersion":"2025-03-26","capabilities":{},
                    "clientInfo":{"name":"integration-test","version":"1.0"}}}
                    """);
            assertThat(initialized.statusCode()).isEqualTo(200);
            String session = initialized.headers().firstValue("Mcp-Session-Id").orElseThrow();
            send(client, session, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");

            HttpResponse<String> listed = send(client, session,
                    "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}");
            assertThat(listed.statusCode()).isEqualTo(200);
            assertThat(listed.body()).contains("getAllTransactions", "getTransactionById", "saveTransaction");

            HttpResponse<String> saved = send(client, session, """
                    {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{
                    "name":"saveTransaction","arguments":{"transaction":{
                    "accountId":"11111111-1111-1111-1111-111111111111","amount":"99.12345678","currency":"MAD","merchantCategory":"mcp-test","country":"MA"}}}}
                    """);
            assertThat(saved.statusCode()).isEqualTo(200);
            assertThat(saved.body()).contains("mcp-test").doesNotContain("\"isError\":true");
            String id = transactionService.getAllTransactions().stream()
                    .filter(a -> "mcp-test".equals(a.getMerchantCategory()))
                    .findFirst().orElseThrow().getId().toString();

            HttpResponse<String> found = send(client, session,
                    "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{"
                    + "\"name\":\"getTransactionById\",\"arguments\":{\"id\":\"" + id + "\"}}}");
            assertThat(found.body()).contains(id, "mcp-test").doesNotContain("\"isError\":true");

            HttpResponse<String> accounts = send(client, session, """
                    {"jsonrpc":"2.0","id":5,"method":"tools/call","params":{
                    "name":"getAllTransactions","arguments":{}}}
                    """);
            assertThat(accounts.body()).contains(id).doesNotContain("\"isError\":true");
        }
    }

    private HttpResponse<String> send(HttpClient client, String session, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream");
        if (session != null) {
            request.header("Mcp-Session-Id", session);
            request.header("MCP-Protocol-Version", "2025-03-26");
        }
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}