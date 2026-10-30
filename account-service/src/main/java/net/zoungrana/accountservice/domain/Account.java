package net.zoungrana.accountservice.domain;

import jakarta.persistence.*;
import lombok.*;
import net.zoungrana.accountservice.enums.Status;

import java.util.UUID;
import java.math.BigDecimal;

@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String customerReference;
    @Enumerated(EnumType.STRING)
    private Status status;
    private String currency;
    @Column(precision = 38, scale = 8)
    private BigDecimal balance;
}
