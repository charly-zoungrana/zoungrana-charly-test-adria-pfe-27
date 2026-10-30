package net.zoungrana.accountservice;

import net.zoungrana.accountservice.domain.Account;
import net.zoungrana.accountservice.enums.Status;
import net.zoungrana.accountservice.service.AccountService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.math.BigDecimal;
import java.math.RoundingMode;

@SpringBootApplication
public class AccountServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }

    @Bean
    CommandLineRunner start(AccountService accountService){

        return args -> {

            List<String> names=List.of("Ali1","Yassine","Rachid");
            names.forEach((name)->{
                Account account=Account.builder()
                        .customerReference(name)
                        .balance(BigDecimal.valueOf(1000 + Math.random() * 10000).setScale(2, RoundingMode.HALF_UP))
                        .currency("MAD")
                        .status(Status.ACTIVE)
                        .build();
                accountService.saveAccount(account);
            });
        };

    }

}
