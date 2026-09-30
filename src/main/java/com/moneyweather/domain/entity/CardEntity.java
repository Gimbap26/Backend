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
    /** 카드 대금이 빠져나가는 계좌. 청구 이벤트가 이 계좌를 물려받는다. */
    private Long paymentAccountId;

    protected CardEntity() {
    }

    public CardEntity(Long userId, String cardCompany, String cardName, int paymentDay, boolean active) {
        this(userId, cardCompany, cardName, paymentDay, active, null);
    }

    public CardEntity(Long userId, String cardCompany, String cardName, int paymentDay, boolean active, Long paymentAccountId) {
        this.userId = userId;
        this.cardCompany = cardCompany;
        this.cardName = cardName;
        this.paymentDay = paymentDay;
        this.active = active;
        this.paymentAccountId = paymentAccountId;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getCardCompany() { return cardCompany; }
    public String getCardName() { return cardName; }
    public int getPaymentDay() { return paymentDay; }
    public boolean isActive() { return active; }
    public Long getPaymentAccountId() { return paymentAccountId; }

    public void update(String cardCompany, String cardName, Integer paymentDay, Boolean active, Long paymentAccountId) {
        if (cardCompany != null) this.cardCompany = cardCompany;
        if (cardName != null) this.cardName = cardName;
        if (paymentDay != null) this.paymentDay = paymentDay;
        if (active != null) this.active = active;
        if (paymentAccountId != null) this.paymentAccountId = paymentAccountId;
    }

    /** 과거 거래와 청구 이력이 카드를 참조하므로 지우지 않고 비활성화한다. */
    public void deactivate() {
        this.active = false;
    }
}
