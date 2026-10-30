package net.zoungrana.transactionservice;

import net.zoungrana.transactionservice.feign.AccountGateway;
import net.zoungrana.transactionservice.domain.Transaction;
import net.zoungrana.transactionservice.service.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;
import java.util.List;

@SpringBootApplication
@EnableFeignClients
public class TransactionServiceApplication {
    private static final Logger log = LoggerFactory.getLogger(TransactionServiceApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(TransactionServiceApplication.class, args);
    }

    @Bean
    @ConditionalOnProperty(name = "bank.demo-data.enabled", havingValue = "true", matchIfMissing = true)
    CommandLineRunner start(TransactionService transactionService, AccountGateway accountGateway) {
        return args -> {
            try {
                var accounts = accountGateway.getAllAccounts().stream()
                        .filter(account -> "ACTIVE".equals(account.getStatus())).toList();
                if (accounts.size() < 2) {
                    log.warn("Demo transactions skipped: at least two active accounts are required");
                    return;
                }
                var first = accounts.get(0);
                var second = accounts.get(1);
                List<Transaction> transactions = List.of(
                        Transaction.builder().accountId(first.getId())
                                .amount(new BigDecimal("250.00")).currency(first.getCurrency())
                                .merchantCategory("GROCERY").country("MA").build(),
                        Transaction.builder().accountId(first.getId())
                                .amount(new BigDecimal("125.50")).currency(first.getCurrency())
                                .merchantCategory("TRANSPORT").country("MA").build(),
                        Transaction.builder().accountId(second.getId())
                                .amount(new BigDecimal("500.00")).currency(second.getCurrency())
                                .merchantCategory("SHOPPING").country("MA").build());
                transactions.forEach(transactionService::saveTransaction);
            } catch (RuntimeException ex) {
                // A remote outage must not prevent the transaction service from starting.
                log.warn("Demo initialization interrupted: {}", ex.getMessage());
            }
        };
    }
}