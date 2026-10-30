package net.zoungrana.accountservice.web.graphQL;

import lombok.RequiredArgsConstructor;
import net.zoungrana.accountservice.domain.Account;
import net.zoungrana.accountservice.enums.Status;
import net.zoungrana.accountservice.service.AccountService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

@Controller
@RequiredArgsConstructor
public class AccountGraphQLController {
    private final AccountService accountService;

    @QueryMapping
    public List<Account> getAllAccounts() {
        return accountService.getAllAccounts();
    }

    @QueryMapping
    public Account getAccountById(@Argument("id") String id) {
        return accountService.getAccountById(UUID.fromString(id));
    }

    @MutationMapping
    public Account saveAccount(@Argument("account") AccountInput input) {
        Account account = Account.builder()
                .customerReference(input.customerReference())
                .status(input.status())
                .currency(input.currency())
                .balance(BigDecimal.valueOf(input.balance()))
                .build();
        accountService.saveAccount(account);
        return account;
    }

    public record AccountInput(String customerReference, Status status,
                               String currency, double balance) {
    }
}