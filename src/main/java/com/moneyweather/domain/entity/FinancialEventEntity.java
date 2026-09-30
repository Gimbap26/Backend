package com.moneyweather.domain.entity;

import com.moneyweather.domain.Enums.*;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "financial_events")
public class FinancialEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private LocalDate eventDate;
    private String title;
    private long amount;
    @Enumerated(EnumType.STRING)
    private Direction direction;
    @Enumerated(EnumType.STRING)
    private EventType eventType;
    @Enumerated(EnumType.STRING)
    private EventStatus status;
    private boolean fixed;
    private Long recurringRuleId;
    private Long cardId;
    /** 결제 완료(PAID) 처리 시 잔액을 바꿀 계좌. */
    private Long accountId;

    protected FinancialEventEntity() {
    }

    public FinancialEventEntity(Long userId, LocalDate eventDate, String title, long amount, Direction direction, EventType eventType, EventStatus status, boolean fixed) {
        this(userId, eventDate, title, amount, direction, eventType, status, fixed, null, null);
    }

    public FinancialEventEntity(Long userId, LocalDate eventDate, String title, long amount, Direction direction, EventType eventType, EventStatus status, boolean fixed, Long recurringRuleId, Long cardId) {
        this(userId, eventDate, title, amount, direction, eventType, status, fixed, recurringRuleId, cardId, null);
    }

    public FinancialEventEntity(Long userId, LocalDate eventDate, String title, long amount, Direction direction, EventType eventType,
                                EventStatus status, boolean fixed, Long recurringRuleId, Long cardId, Long accountId) {
        this.accountId = accountId;
        this.userId = userId;
        this.eventDate = eventDate;
        this.title = title;
        this.amount = amount;
        this.direction = direction;
        this.eventType = eventType;
        this.status = status;
        this.fixed = fixed;
        this.recurringRuleId = recurringRuleId;
        this.cardId = cardId;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public LocalDate getEventDate() { return eventDate; }
    public String getTitle() { return title; }
    public long getAmount() { return amount; }
    public Direction getDirection() { return direction; }
    public EventType getEventType() { return eventType; }
    public EventStatus getStatus() { return status; }
    public boolean isFixed() { return fixed; }
    public Long getRecurringRuleId() { return recurringRuleId; }
    public Long getCardId() { return cardId; }
    public Long getAccountId() { return accountId; }

    /** 자동 생성 이벤트를 원본(반복 규칙, 카드 사용분) 변화에 맞춰 다시 맞춘다. 연결 계좌도 원본을 따른다. */
    public void syncGenerated(String title, long amount, Long accountId) {
        this.title = title;
        this.amount = amount;
        this.accountId = accountId;
    }

    /** 상태 변경은 잔액 반영이 뒤따르므로 {@link #changeStatus}로만 한다. */
    public void update(LocalDate eventDate, String title, Long amount, Boolean fixed, Long accountId) {
        if (eventDate != null) this.eventDate = eventDate;
        if (title != null) this.title = title;
        if (amount != null) this.amount = amount;
        if (fixed != null) this.fixed = fixed;
        if (accountId != null) this.accountId = accountId;
    }

    public void changeStatus(EventStatus status) {
        this.status = status;
    }

    public void cancel() {
        this.status = EventStatus.CANCELED;
    }
}
