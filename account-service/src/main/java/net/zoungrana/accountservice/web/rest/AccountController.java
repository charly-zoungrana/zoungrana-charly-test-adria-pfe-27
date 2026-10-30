package net.zoungrana.accountservice.web.rest;

import lombok.RequiredArgsConstructor;
import net.zoungrana.accountservice.domain.Account;
import net.zoungrana.accountservice.service.AccountService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AccountController {
    private final AccountService accountService;

    @GetMapping("/accounts")
    public List<Account> getAllAccounts() {
        return accountService.getAllAccounts();
    }

    @GetMapping("/accounts/{id}")
    public Account getAccountById(@PathVariable UUID id){
        return accountService.getAccountById(id);
    }

    @PostMapping("/accounts")
    public void saveCustomer(Account account){
        accountService.saveAccount(account);
    }

    @PostMapping("/accounts/{id}/debits")
    public Account debitAccount(@PathVariable UUID id, @RequestBody DebitRequest request) {
        return accountService.debitAccount(id, request.operationId(), request.amount(), request.currency());
    }

    public record DebitRequest(UUID operationId, BigDecimal amount, String currency) { }
}
