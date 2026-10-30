package net.zoungrana.accountservice.repository;

import net.zoungrana.accountservice.domain.AccountDebit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AccountDebitRepository extends JpaRepository<AccountDebit, UUID> {
}