package net.zoungrana.transactionservice.service;

import lombok.RequiredArgsConstructor;
import net.zoungrana.transactionservice.domain.Transaction;
import net.zoungrana.transactionservice.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionStateService {
    private final TransactionRepository repository;

    @Transactional
    public Transaction prepare(Transaction transaction) {
        transaction.setProcessingStatus("PENDING");
        return repository.saveAndFlush(transaction);
    }

    @Transactional
    public Transaction complete(UUID id) {
        Transaction transaction = repository.findByIdForUpdate(id).orElseThrow();
        transaction.setProcessingStatus("COMPLETED");
        return repository.saveAndFlush(transaction);
    }

    @Transactional
    public void fail(UUID id) {
        Transaction transaction = repository.findByIdForUpdate(id).orElseThrow();
        if (!"COMPLETED".equals(transaction.getProcessingStatus())) {
            transaction.setProcessingStatus("FAILED");
        }
    }
}