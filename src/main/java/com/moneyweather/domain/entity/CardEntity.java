package com.moneyweather.domain.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "cards")
public class CardEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private String cardCompany;
    private String cardName;
    private int paymentDay;
    private boolean active;

    protected CardEntity() {
    }

    public CardEntity(Long userId, String cardCompany, String cardName, int paymentDay, boolean active) {
        this.userId = userId;
        this.cardCompany = cardCompany;
        this.cardName = cardName;
        this.paymentDay = paymentDay;
        this.active = active;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getCardCompany() { return cardCompany; }
    public String getCardName() { return cardName; }
    public int getPaymentDay() { return paymentDay; }
    public boolean isActive() { return active; }
}
