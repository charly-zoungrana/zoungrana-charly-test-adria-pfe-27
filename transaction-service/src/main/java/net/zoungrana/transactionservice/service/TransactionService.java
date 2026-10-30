package net.zoungrana.transactionservice.service;

import lombok.RequiredArgsConstructor;
import net.zoungrana.transactionservice.domain.Transaction;
import net.zoungrana.transactionservice.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {
    private final TransactionRepository transactionRepository;

    @Value("${bank.transaction.max-amount:1000}")
    private BigDecimal maxAmount;

    public List<Transaction> getAllTransactions() {
        return transactionRepository.findAll();
    }

    public Transaction getTransactionById(UUID id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
    }

    @Transactional
    public Transaction saveTransaction(Transaction transaction) {
        if (transaction.getId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The transaction id is generated automatically");
        }
        if (transaction.getAccountId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account id is required");
        }
        if (transaction.getAmount() == null || transaction.getAmount().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be strictly positive");
        }
        if (transaction.getAmount().compareTo(maxAmount) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount exceeds the configured limit");
        }
        if (transaction.getAmount().stripTrailingZeros().scale() > 8
                || transaction.getAmount().compareTo(BigDecimal.TEN.pow(30)) >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount exceeds the supported decimal precision");
        }
        if (transaction.getCurrency() == null || transaction.getCurrency().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Currency is required");
        }
        transaction.setCreatedAt(Instant.now());
        return transactionRepository.save(transaction);
    }
}