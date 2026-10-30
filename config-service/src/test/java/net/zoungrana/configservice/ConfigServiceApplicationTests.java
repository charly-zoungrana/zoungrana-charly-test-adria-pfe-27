package net.zoungrana.configservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.net.URI;
import java.net.http.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"eureka.client.enabled=false", "spring.cloud.discovery.enabled=false"})
class ConfigServiceApplicationTests {
    @LocalServerPort int port;

    @Test
    void servesSharedCeilingForEveryClient() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            for (String application : new String[]{"account-service", "transaction-service", "gateway-service"}) {
                var response = client.send(HttpRequest.newBuilder(URI.create(
                        "http://localhost:" + port + "/" + application + "/default"))
                        .GET().build(), HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
                assertThat(response.body()).contains("bank.transaction.max-amount", "1000", "propertySources");
            }
        }
    }
}