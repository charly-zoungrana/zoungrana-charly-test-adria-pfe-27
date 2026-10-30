package net.zoungrana.transactionservice;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import net.zoungrana.transactionservice.feign.AccountGateway;
import net.zoungrana.transactionservice.domain.Transaction;
import net.zoungrana.transactionservice.repository.TransactionRepository;
import net.zoungrana.transactionservice.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenFeignIntegrationTests extends FeignTestSupport {
    @Autowired TransactionService transactions;
    @Autowired TransactionRepository repository;
    @Autowired AccountGateway accounts;
    @Autowired CircuitBreakerRegistry breakers;

    @AfterEach
    @BeforeEach
    void resetCircuit() { breakers.circuitBreaker("accountService").reset(); }

    @Test
    void realFeignReadAndDebitReduceBalanceExactly() {
        UUID id = UUID.randomUUID();
        BALANCES.put(id, new BigDecimal("1000.12345678"));
        Transaction saved = transactions.saveTransaction(transaction(id, "100.00000001"));
        assertThat(saved.getProcessingStatus()).isEqualTo("COMPLETED");
        assertThat(accounts.getAccountById(id).balance()).isEqualByComparingTo("900.12345677");
        assertThat(GET_CALLS.get(id).get()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void rejectsMissingBlockedWrongCurrencyAndInsufficientBalanceBeforePersistence() {
        UUID missing = UUID.randomUUID(); MISSING.add(missing);
        UUID blocked = UUID.randomUUID(); STATUSES.put(blocked, "BLOCKED");
        UUID wrongCurrency = UUID.randomUUID(); CURRENCIES.put(wrongCurrency, "EUR");
        UUID insufficient = UUID.randomUUID(); BALANCES.put(insufficient, BigDecimal.ONE);
        long initial = repository.count();
        for (UUID id : new UUID[]{missing, blocked, wrongCurrency, insufficient}) {
            assertThatThrownBy(() -> transactions.saveTransaction(transaction(id, "20")))
                    .isInstanceOf(ResponseStatusException.class);
        }
        assertThat(repository.count()).isEqualTo(initial);
        assertThat(breakers.circuitBreaker("accountService").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void retriesReadsAndOpensCircuitDuringOutageWithoutSavingTransactions() {
        UUID id = UUID.randomUUID(); UNAVAILABLE.add(id);
        long initial = repository.count();
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> transactions.saveTransaction(transaction(id, "20")))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            ex -> assertThat(ex.getStatusCode().value()).isEqualTo(503));
        }
        assertThat(GET_CALLS.get(id).get()).isEqualTo(6);
        assertThat(breakers.circuitBreaker("accountService").getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThatThrownBy(() -> accounts.getAccountById(id)).isInstanceOf(ResponseStatusException.class);
        assertThat(GET_CALLS.get(id).get()).isEqualTo(6);
        assertThat(repository.count()).isEqualTo(initial);
    }

    @Test
    void readTimeoutFailsWithoutCreatingATransaction() {
        UUID id = UUID.randomUUID(); SLOW.add(id);
        long initial = repository.count();
        assertThatThrownBy(() -> transactions.saveTransaction(transaction(id, "20")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(503));
        assertThat(repository.count()).isEqualTo(initial);
    }

    @Test
    void lostDebitResponseCanBeRecoveredWithoutDoubleDebit() {
        UUID id = UUID.randomUUID(); BALANCES.put(id, new BigDecimal("1000")); LOST_REPLY.add(id);
        assertThatThrownBy(() -> transactions.saveTransaction(transaction(id, "25.50")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(503));
        Transaction pending = repository.findAll().stream().filter(t -> id.equals(t.getAccountId())).findFirst().orElseThrow();
        assertThat(pending.getProcessingStatus()).isEqualTo("PENDING");
        assertThat(BALANCES.get(id)).isEqualByComparingTo("974.50");
        Transaction recovered = transactions.retryTransaction(pending.getId());
        assertThat(recovered.getProcessingStatus()).isEqualTo("COMPLETED");
        assertThat(BALANCES.get(id)).isEqualByComparingTo("974.50");
        transactions.retryTransaction(pending.getId());
        assertThat(BALANCES.get(id)).isEqualByComparingTo("974.50");
    }

    @Test
    void explicitDebitRejectionMarksTransactionFailed() {
        UUID id = UUID.randomUUID(); REJECT_DEBIT.add(id);
        assertThatThrownBy(() -> transactions.saveTransaction(transaction(id, "20")))
                .isInstanceOf(ResponseStatusException.class);
        Transaction failed = repository.findAll().stream().filter(t -> id.equals(t.getAccountId())).findFirst().orElseThrow();
        assertThat(failed.getProcessingStatus()).isEqualTo("FAILED");
        assertThatThrownBy(() -> transactions.retryTransaction(failed.getId())).isInstanceOf(ResponseStatusException.class);
    }

    private Transaction transaction(UUID id, String amount) {
        return Transaction.builder().accountId(id).amount(new BigDecimal(amount))
                .currency("MAD").merchantCategory("FEIGN-TEST").country("MA").build();
    }
}