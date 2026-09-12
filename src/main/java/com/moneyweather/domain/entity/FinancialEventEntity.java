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

    protected FinancialEventEntity() {
    }

    public FinancialEventEntity(Long userId, LocalDate eventDate, String title, long amount, Direction direction, EventType eventType, EventStatus status, boolean fixed) {
        this.userId = userId;
        this.eventDate = eventDate;
        this.title = title;
        this.amount = amount;
        this.direction = direction;
        this.eventType = eventType;
        this.status = status;
        this.fixed = fixed;
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

    public void update(LocalDate eventDate, String title, Long amount, EventStatus status, Boolean fixed) {
        if (eventDate != null) this.eventDate = eventDate;
        if (title != null) this.title = title;
        if (amount != null) this.amount = amount;
        if (status != null) this.status = status;
        if (fixed != null) this.fixed = fixed;
    }

    public void cancel() {
        this.status = EventStatus.CANCELED;
    }
}
