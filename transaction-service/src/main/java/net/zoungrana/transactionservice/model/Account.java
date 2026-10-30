package net.zoungrana.transactionservice.model;

import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Account {
    private UUID id;
    private String customerReference;
    private String status;
    private String currency;
    private BigDecimal balance;
}