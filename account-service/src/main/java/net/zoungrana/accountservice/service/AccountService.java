package net.zoungrana.accountservice.service;

import lombok.RequiredArgsConstructor;
import net.zoungrana.accountservice.domain.Account;
import net.zoungrana.accountservice.domain.AccountDebit;
import net.zoungrana.accountservice.enums.Status;
import net.zoungrana.accountservice.repository.AccountRepository;
import net.zoungrana.accountservice.repository.AccountDebitRepository;
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
public class AccountService {
    private final AccountRepository accountRepository;
    private final AccountDebitRepository accountDebitRepository;

    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }

    public Account getAccountById(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    public void saveAccount(Account account) {
        accountRepository.save(account);
    }

    @Transactional
    public Account debitAccount(UUID accountId, UUID operationId, BigDecimal amount, String currency) {
        if (operationId == null || amount == null || amount.signum() <= 0
                || amount.stripTrailingZeros().scale() > 8 || amount.compareTo(BigDecimal.TEN.pow(30)) >= 0
                || currency == null || currency.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid debit request");
        }
        // The account lock serializes concurrent debits and duplicate requests.
        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        var previous = accountDebitRepository.findById(operationId);
        if (previous.isPresent()) {
            AccountDebit debit = previous.get();
            if (!debit.getAccountId().equals(accountId) || debit.getAmount().compareTo(amount) != 0
                    || !debit.getCurrency().equals(currency)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Operation id already used for a different debit");
            }
            return account;
        }
        if (account.getStatus() != Status.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account is not active");
        }
        if (!currency.equals(account.getCurrency())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Currency does not match account currency");
        }
        if (account.getBalance() == null || account.getBalance().compareTo(amount) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient balance");
        }
        account.setBalance(account.getBalance().subtract(amount));
        accountDebitRepository.save(AccountDebit.builder().operationId(operationId)
                .accountId(accountId).amount(amount).currency(currency).createdAt(Instant.now()).build());
        return accountRepository.save(account);
    }
}