package net.zoungrana.transactionservice.service;

import lombok.RequiredArgsConstructor;
import net.zoungrana.transactionservice.domain.Transaction;
import net.zoungrana.transactionservice.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import net.zoungrana.transactionservice.feign.AccountGateway;
import net.zoungrana.transactionservice.feign.AccountClient;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import net.zoungrana.transactionservice.model.Account;

@Service
@RequiredArgsConstructor
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final TransactionStateService transactionStateService;
    private final AccountGateway accountGateway;

    @Value("${bank.transaction.max-amount}")
    private BigDecimal maxAmount;

    public List<Transaction> getAllTransactions() {
        List<Transaction> transactions = transactionRepository.findAll();
        Map<UUID, Account> accounts = new HashMap<>();
        transactions.forEach(transaction -> transaction.setAccount(accounts.computeIfAbsent(
                transaction.getAccountId(), this::loadAccount)));
        return transactions;
    }

    public Transaction getTransactionById(UUID id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));

        transaction.setAccount(loadAccount(transaction.getAccountId()));
        return transaction;
    }

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
        Account account = accountGateway.getAccountById(transaction.getAccountId());
        if (account == null || !transaction.getAccountId().equals(account.getId()) || account.getBalance() == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Invalid account service response");
        }
        if (!"ACTIVE".equals(account.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account is not active");
        }
        if (!transaction.getCurrency().equals(account.getCurrency())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Currency does not match account currency");
        }
        if (account.getBalance().compareTo(transaction.getAmount()) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient balance");
        }
        transaction.setCreatedAt(Instant.now());
        return processDebit(transactionStateService.prepare(transaction));
    }

    public Transaction retryTransaction(UUID id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
        if ("COMPLETED".equals(transaction.getProcessingStatus())) {
            transaction.setAccount(loadAccount(transaction.getAccountId()));
            return transaction;
        }
        if (!"PENDING".equals(transaction.getProcessingStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending transactions can be retried");
        }
        return processDebit(transaction);
    }

    private Transaction processDebit(Transaction transaction) {
        Account updatedAccount;
        try {

            updatedAccount = accountGateway.debitAccount(transaction.getAccountId(), new AccountClient.DebitRequest(
                    transaction.getId(), transaction.getAmount(), transaction.getCurrency()));
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                transactionStateService.fail(transaction.getId());
                throw ex;
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Transaction " + transaction.getId() + " is PENDING; retry this id when account-service recovers", ex);
        }
        try {
            Transaction completed = transactionStateService.complete(transaction.getId());
            completed.setAccount(updatedAccount);
            return completed;
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Transaction " + transaction.getId() + " requires reconciliation; retry this id", ex);
        }
    }

    private Account loadAccount(UUID id) {
        Account account = accountGateway.getAccountById(id);
        if (account == null || !id.equals(account.getId())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Invalid account service response");
        }
        return account;
    }
}