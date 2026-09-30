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
    /** 같은 계좌를 동시에 수정할 때 한쪽 변경이 조용히 사라지지 않도록 막는다. */
    @Version
    private long version;

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

    /** 거래·결제로 인한 잔액 증감. 음수면 출금이다. */
    public void adjustBalance(long delta) {
        this.balance += delta;
    }

    public void update(String bankName, String accountName, Long balance, String purpose, Boolean includedInAssets) {
        if (bankName != null) this.bankName = bankName;
        if (accountName != null) this.accountName = accountName;
        if (balance != null) this.balance = balance;
        if (purpose != null) this.purpose = purpose;
        if (includedInAssets != null) this.includedInAssets = includedInAssets;
    }
}
