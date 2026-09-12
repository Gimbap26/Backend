package com.moneyweather.domain.entity;

import com.moneyweather.domain.Enums.TransactionType;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "transactions")
public class TransactionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private LocalDate transactionDate;
    private String merchant;
    private long amount;
    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;
    private Long categoryId;

    protected TransactionEntity() {
    }

    public TransactionEntity(Long userId, LocalDate transactionDate, String merchant, long amount, TransactionType transactionType, Long categoryId) {
        this.userId = userId;
        this.transactionDate = transactionDate;
        this.merchant = merchant;
        this.amount = amount;
        this.transactionType = transactionType;
        this.categoryId = categoryId;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public LocalDate getTransactionDate() { return transactionDate; }
    public String getMerchant() { return merchant; }
    public long getAmount() { return amount; }
    public TransactionType getTransactionType() { return transactionType; }
    public Long getCategoryId() { return categoryId; }
    public void changeCategory(Long categoryId) { this.categoryId = categoryId; }
}
