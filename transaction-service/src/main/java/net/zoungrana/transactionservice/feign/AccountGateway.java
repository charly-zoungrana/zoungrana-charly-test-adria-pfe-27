package net.zoungrana.transactionservice.feign;

import lombok.RequiredArgsConstructor;
import net.zoungrana.transactionservice.model.Account;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountGateway {
    private final AccountClient accountClient;

    public Account getAccountById(UUID id) {
        return accountClient.getAccountById(id);
    }

    public List<Account> getAllAccounts() {
        return accountClient.getAllAccounts();
    }

    public Account debitAccount(UUID id, AccountClient.DebitRequest request) {
        return accountClient.debitAccount(id, request);
    }
}