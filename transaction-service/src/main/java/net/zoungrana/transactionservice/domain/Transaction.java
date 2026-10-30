package net.zoungrana.transactionservice.domain;

import jakarta.persistence.*;
import net.zoungrana.transactionservice.model.Account;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private UUID accountId;
    @Column(nullable = false, precision = 38, scale = 8)
    private BigDecimal amount;
    @Column(nullable = false)
    private String currency;
    private String merchantCategory;
    private String country;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private String processingStatus;

    @Transient
    private Account account;

}