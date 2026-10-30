package net.zoungrana.transactionservice.feign;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class AccountGateway {
    private final AccountClient accountClient;
    private final CircuitBreakerRegistry circuitBreakers;
    private final RetryRegistry retries;

    public AccountClient.AccountResponse getAccountById(UUID id) {
        return call(() -> retries.retry("accountService").executeSupplier(() -> accountClient.getAccountById(id)));
    }

    public List<AccountClient.AccountResponse> getAllAccounts() {
        return call(() -> retries.retry("accountService").executeSupplier(accountClient::getAllAccounts));
    }

    public void debitAccount(UUID id, AccountClient.DebitRequest request) {
        call(() -> accountClient.debitAccount(id, request));
    }

    private <T> T call(Supplier<T> operation) {
        try {
            return circuitBreakers.circuitBreaker("accountService").executeSupplier(operation);
        } catch (FeignException ex) {
            if (ex.status() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found", ex);
            }
            if (ex.status() >= 400 && ex.status() < 500) {
                throw new ResponseStatusException(HttpStatus.valueOf(ex.status()), "Account service rejected the operation", ex);
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Account service is temporarily unavailable", ex);
        } catch (CallNotPermittedException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Account service circuit breaker is open", ex);
        }
    }
}