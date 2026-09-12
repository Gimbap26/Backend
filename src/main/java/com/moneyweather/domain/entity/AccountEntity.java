package com.moneyweather.domain.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "accounts")
public class AccountEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private String bankName;
    private String accountName;
    private long balance;
    private String purpose;
    private boolean includedInAssets;

    protected AccountEntity() {
    }

    public AccountEntity(Long userId, String bankName, String accountName, long balance, String purpose, boolean includedInAssets) {
        this.userId = userId;
        this.bankName = bankName;
        this.accountName = accountName;
        this.balance = balance;
        this.purpose = purpose;
        this.includedInAssets = includedInAssets;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getBankName() { return bankName; }
    public String getAccountName() { return accountName; }
    public long getBalance() { return balance; }
    public String getPurpose() { return purpose; }
    public boolean isIncludedInAssets() { return includedInAssets; }
}
