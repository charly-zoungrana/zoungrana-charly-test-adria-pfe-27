package net.zoungrana.transactionservice.web.graplQL;

import lombok.RequiredArgsConstructor;
import net.zoungrana.transactionservice.domain.Transaction;
import net.zoungrana.transactionservice.service.TransactionService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import net.zoungrana.transactionservice.model.Account;

@Controller
@RequiredArgsConstructor
public class TransactionGraphQLController {
    private final TransactionService transactionService;

    @QueryMapping
    public List<Transaction> getAllTransactions() {
        return transactionService.getAllTransactions();
    }

    @QueryMapping
    public Transaction getTransactionById(@Argument("id") String id) {
        return transactionService.getTransactionById(UUID.fromString(id));
    }

    @MutationMapping
    public Transaction saveTransaction(@Argument("transaction") TransactionInput input) {
        return transactionService.saveTransaction(Transaction.builder()
                .accountId(UUID.fromString(input.accountId()))
                .amount(new BigDecimal(input.amount()))
                .currency(input.currency())
                .merchantCategory(input.merchantCategory())
                .country(input.country())
                .build());
    }

    // Decimal strings preserve BigDecimal precision without conversion to double.
    @SchemaMapping(typeName = "Transaction", field = "amount")
    public String amount(Transaction transaction) {
        return transaction.getAmount().toPlainString();
    }

    @SchemaMapping(typeName = "Transaction", field = "createdAt")
    public String createdAt(Transaction transaction) {
        return transaction.getCreatedAt().toString();
    }

    @SchemaMapping(typeName = "Account", field = "balance")
    public String balance(Account account) {
        return account.getBalance() == null ? null : account.getBalance().toPlainString();
    }
    public record TransactionInput(String accountId, String amount, String currency,
                                   String merchantCategory, String country) {
    }
}