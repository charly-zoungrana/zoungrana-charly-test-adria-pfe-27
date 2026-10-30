package net.zoungrana.transactionservice.feign;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import net.zoungrana.transactionservice.model.Account;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@FeignClient(name = "account-service", url = "${bank.account-service.url:}")
public interface AccountClient {
    @GetMapping("/accounts")
    @CircuitBreaker(name = "accountService", fallbackMethod = "defaultAccounts")
    @Retry(name = "accountService")
    List<Account> getAllAccounts();

    @GetMapping("/accounts/{id}")
    @CircuitBreaker(name = "accountService", fallbackMethod = "defaultAccount")
    @Retry(name = "accountService")
    Account getAccountById(@PathVariable("id") UUID id);

    @PostMapping("/accounts/{id}/debits")
    @CircuitBreaker(name = "accountService", fallbackMethod = "defaultDebit")
    Account debitAccount(@PathVariable("id") UUID id, @RequestBody DebitRequest request);

    default List<Account> defaultAccounts(Throwable cause) {
        throw accountServiceFailure(cause);
    }

    default Account defaultAccount(UUID id, Throwable cause) {
        return Account.builder()
                .id(id)
                .customerReference("unknown")
                .build();
    }

    default Account defaultDebit(UUID id, DebitRequest request, Throwable cause) {
        return Account.builder()
                .id(id)
                .customerReference("unknown")
                .build();
    }

    private static ResponseStatusException accountServiceFailure(Throwable cause) {
        if (cause instanceof FeignException ex) {
            if (ex.status() == 404) {
                return new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found", cause);
            }
            if (ex.status() >= 400 && ex.status() < 500) {
                return new ResponseStatusException(HttpStatus.valueOf(ex.status()), "Account service rejected the operation", cause);
            }
        }
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Account service is temporarily unavailable or its circuit breaker is open", cause);
    }

    record DebitRequest(UUID operationId, BigDecimal amount, String currency) { }
}