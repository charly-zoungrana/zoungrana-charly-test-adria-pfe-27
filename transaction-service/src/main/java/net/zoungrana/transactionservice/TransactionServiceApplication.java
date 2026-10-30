package net.zoungrana.transactionservice;

import net.zoungrana.transactionservice.domain.Transaction;
import net.zoungrana.transactionservice.service.TransactionService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@SpringBootApplication
public class TransactionServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TransactionServiceApplication.class, args);
    }

    @Bean
    CommandLineRunner start(TransactionService transactionService) {
        return args -> {

            UUID firstAccountId = UUID.fromString("11111111-1111-1111-1111-111111111111");
            UUID secondAccountId = UUID.fromString("22222222-2222-2222-2222-222222222222");
            List<Transaction> transactions = List.of(
                    Transaction.builder().accountId(firstAccountId)
                            .amount(new BigDecimal("250.00")).currency("MAD")
                            .merchantCategory("GROCERY").country("MA").build(),
                    Transaction.builder().accountId(firstAccountId)
                            .amount(new BigDecimal("125.50")).currency("MAD")
                            .merchantCategory("TRANSPORT").country("MA").build(),
                    Transaction.builder().accountId(secondAccountId)
                            .amount(new BigDecimal("500.00")).currency("MAD")
                            .merchantCategory("SHOPPING").country("MA").build());
            transactions.forEach(transactionService::saveTransaction);
        };
    }
}