package net.zoungrana.accountservice.mcp;

import lombok.RequiredArgsConstructor;
import net.zoungrana.accountservice.domain.Account;
import net.zoungrana.accountservice.enums.Status;
import net.zoungrana.accountservice.service.AccountService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountMcpService {
    private final AccountService accountService;

    @McpTool(name = "getAllAccounts", description = "List all bank accounts")
    public List<Account> getAllAccounts() {
        return accountService.getAllAccounts();
    }

    @McpTool(name = "getAccountById", description = "Find a bank account by its UUID")
    public Account getAccountById(
            @McpToolParam(description = "Bank account UUID", required = true) String id) {
        return accountService.getAccountById(UUID.fromString(id));
    }

    @McpTool(name = "saveAccount", description = "Save a bank account and return the saved account")
    public Account saveAccount(
            @McpToolParam(description = "New account: customerReference, status (ACTIVE or BLOCKED), currency, balance", required = true)
            AccountInput account) {
        Account savedAccount = Account.builder()
                .customerReference(account.customerReference())
                .status(account.status())
                .currency(account.currency())
                .balance(account.balance())
                .build();
        accountService.saveAccount(savedAccount);
        return savedAccount;
    }

    public record AccountInput(String customerReference, Status status,
                               String currency, double balance) {
    }
}