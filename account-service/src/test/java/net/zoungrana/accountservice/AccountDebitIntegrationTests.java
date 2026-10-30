package net.zoungrana.accountservice;

import net.zoungrana.accountservice.domain.Account;
import net.zoungrana.accountservice.enums.Status;
import net.zoungrana.accountservice.repository.AccountRepository;
import net.zoungrana.accountservice.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@org.springframework.test.context.TestPropertySource(properties = {"spring.cloud.config.enabled=false", "spring.cloud.discovery.enabled=false", "BANK_CONFIG_IMPORT=optional:classpath:/disabled-config.properties", "bank.transaction.max-amount=1000"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountDebitIntegrationTests {
    @Autowired AccountService accounts;
    @Autowired AccountRepository repository;
    @LocalServerPort int port;

    @Test
    void repeatedOperationDebitsOnlyOnceWithExactDecimalArithmetic() {
        Account account = create("100.12345678", Status.ACTIVE);
        UUID operation = UUID.randomUUID();
        accounts.debitAccount(account.getId(), operation, new BigDecimal("0.00000001"), "MAD");
        accounts.debitAccount(account.getId(), operation, new BigDecimal("0.00000001"), "MAD");
        assertThat(accounts.getAccountById(account.getId()).getBalance()).isEqualByComparingTo("100.12345677");
        assertThatThrownBy(() -> accounts.debitAccount(account.getId(), operation, BigDecimal.ONE, "MAD"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void rejectsBlockedWrongCurrencyAndInsufficientFunds() {
        Account blocked = create("100", Status.BLOCKED);
        Account active = create("10", Status.ACTIVE);
        assertThatThrownBy(() -> accounts.debitAccount(blocked.getId(), UUID.randomUUID(), BigDecimal.ONE, "MAD"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> accounts.debitAccount(active.getId(), UUID.randomUUID(), BigDecimal.ONE, "EUR"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> accounts.debitAccount(active.getId(), UUID.randomUUID(), new BigDecimal("11"), "MAD"))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(accounts.getAccountById(active.getId()).getBalance()).isEqualByComparingTo("10");
    }

    @Test
    void concurrentDebitsCannotOverdrawAccount() throws Exception {
        Account account = create("100", Status.ACTIVE);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Callable<Boolean> debit = () -> {
                start.await();
                try {
                    accounts.debitAccount(account.getId(), UUID.randomUUID(), new BigDecimal("80"), "MAD");
                    return true;
                } catch (ResponseStatusException ex) {
                    assertThat(ex.getStatusCode().value()).isEqualTo(409);
                    return false;
                }
            };
            Future<Boolean> first = executor.submit(debit);
            Future<Boolean> second = executor.submit(debit);
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS) ^ second.get(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(accounts.getAccountById(account.getId()).getBalance()).isEqualByComparingTo("20");
    }

    @Test
    void debitEndpointIsIdempotentOverHttp() throws Exception {
        Account account = create("100", Status.ACTIVE);
        String body = "{\"operationId\":\"" + UUID.randomUUID() + "\",\"amount\":25.50,\"currency\":\"MAD\"}";
        try (HttpClient client = HttpClient.newHttpClient()) {
            for (int i = 0; i < 2; i++) {
                HttpResponse<String> response = client.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/accounts/" + account.getId() + "/debits"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            }
        }
        assertThat(accounts.getAccountById(account.getId()).getBalance()).isEqualByComparingTo("74.50");
    }

    private Account create(String balance, Status status) {
        return repository.save(Account.builder().customerReference("debit-test").currency("MAD")
                .status(status).balance(new BigDecimal(balance)).build());
    }
}