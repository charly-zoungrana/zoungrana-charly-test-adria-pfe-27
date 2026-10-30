package net.zoungrana.transactionservice.mcp;

import lombok.RequiredArgsConstructor;
import net.zoungrana.transactionservice.domain.Transaction;
import net.zoungrana.transactionservice.service.TransactionService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionMcpService {
    private final TransactionService transactionService;

    @McpTool(name = "getAllTransactions", description = "List all bank transactions")
    public List<Transaction> getAllTransactions() {
        return transactionService.getAllTransactions();
    }

    @McpTool(name = "getTransactionById", description = "Find a bank transaction by its UUID")
    public Transaction getTransactionById(
            @McpToolParam(description = "Transaction UUID", required = true) String id) {
        return transactionService.getTransactionById(UUID.fromString(id));
    }

    @McpTool(name = "saveTransaction", description = "Record a new bank transaction")
    public Transaction saveTransaction(
            @McpToolParam(description = "Transaction with accountId, amount as an exact decimal string, currency, merchantCategory and country", required = true)
            TransactionInput transaction) {
        return transactionService.saveTransaction(Transaction.builder()
                .accountId(UUID.fromString(transaction.accountId()))
                .amount(new BigDecimal(transaction.amount()))
                .currency(transaction.currency())
                .merchantCategory(transaction.merchantCategory())
                .country(transaction.country())
                .build());
    }

    public record TransactionInput(String accountId, String amount, String currency,
                                   String merchantCategory, String country) {
    }
}