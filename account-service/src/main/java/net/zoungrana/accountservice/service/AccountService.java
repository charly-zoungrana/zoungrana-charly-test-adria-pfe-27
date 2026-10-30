package net.zoungrana.accountservice.service;

import lombok.RequiredArgsConstructor;
import net.zoungrana.accountservice.domain.Account;
import net.zoungrana.accountservice.repository.AccountRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;


    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }

    public Account getAccountById(UUID id){
        return accountRepository.findById(id)
                .orElseThrow(()->new RuntimeException("Customer not found"));
    }

    public void saveAccount(Account account){
        accountRepository.save(account);
    }

}
