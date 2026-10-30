package net.zoungrana.accountservice.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AccountDebit {
    @Id
    private UUID operationId;
    @Column(nullable = false)
    private UUID accountId;
    @Column(nullable = false, precision = 38, scale = 8)
    private BigDecimal amount;
    @Column(nullable = false)
    private String currency;
    @Column(nullable = false)
    private Instant createdAt;
}