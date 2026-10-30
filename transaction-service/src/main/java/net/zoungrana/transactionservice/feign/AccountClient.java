package net.zoungrana.transactionservice.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@FeignClient(name = "account-service", url = "${bank.account-service.url}")
public interface AccountClient {
    @GetMapping("/accounts")
    List<AccountResponse> getAllAccounts();

    @GetMapping("/accounts/{id}")
    AccountResponse getAccountById(@PathVariable("id") UUID id);

    @PostMapping("/accounts/{id}/debits")
    AccountResponse debitAccount(@PathVariable("id") UUID id, @RequestBody DebitRequest request);

    record AccountResponse(UUID id, String customerReference, String status, String currency, BigDecimal balance) { }
    record DebitRequest(UUID operationId, BigDecimal amount, String currency) { }
}