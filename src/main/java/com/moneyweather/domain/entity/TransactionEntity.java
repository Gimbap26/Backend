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
    private Long cardId;
    /** 돈이 빠져나가거나 들어온 계좌. 카드 거래는 여기가 비어 있고 카드 청구 시점에 계좌에서 빠진다. */
    private Long accountId;
    /** 이체(TRANSFER)일 때 돈을 받는 계좌. */
    private Long transferAccountId;

    protected TransactionEntity() {
    }

    public TransactionEntity(Long userId, LocalDate transactionDate, String merchant, long amount, TransactionType transactionType, Long categoryId) {
        this(userId, transactionDate, merchant, amount, transactionType, categoryId, null);
    }

    public TransactionEntity(Long userId, LocalDate transactionDate, String merchant, long amount, TransactionType transactionType, Long categoryId, Long cardId) {
        this(userId, transactionDate, merchant, amount, transactionType, categoryId, cardId, null, null);
    }

    public TransactionEntity(Long userId, LocalDate transactionDate, String merchant, long amount, TransactionType transactionType,
                             Long categoryId, Long cardId, Long accountId, Long transferAccountId) {
        this.userId = userId;
        this.transactionDate = transactionDate;
        this.merchant = merchant;
        this.amount = amount;
        this.transactionType = transactionType;
        this.categoryId = categoryId;
        this.cardId = cardId;
        this.accountId = accountId;
        this.transferAccountId = transferAccountId;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public LocalDate getTransactionDate() { return transactionDate; }
    public String getMerchant() { return merchant; }
    public long getAmount() { return amount; }
    public TransactionType getTransactionType() { return transactionType; }
    public Long getCategoryId() { return categoryId; }
    public Long getCardId() { return cardId; }
    public Long getAccountId() { return accountId; }
    public Long getTransferAccountId() { return transferAccountId; }
    public void changeCategory(Long categoryId) { this.categoryId = categoryId; }

    /** 서비스에서 병합·검증을 마친 최종 값으로 교체한다. 결제 수단을 바꿀 때 이전 값을 비워야 하므로 null 도 그대로 반영한다. */
    public void replace(LocalDate transactionDate, String merchant, long amount, TransactionType transactionType,
                        Long categoryId, Long cardId, Long accountId, Long transferAccountId) {
        this.transactionDate = transactionDate;
        this.merchant = merchant;
        this.amount = amount;
        this.transactionType = transactionType;
        this.categoryId = categoryId;
        this.cardId = cardId;
        this.accountId = accountId;
        this.transferAccountId = transferAccountId;
    }
}
