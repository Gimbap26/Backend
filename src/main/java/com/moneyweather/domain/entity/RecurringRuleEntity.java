package com.moneyweather.domain.entity;

import com.moneyweather.domain.Enums.*;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "recurring_rules")
public class RecurringRuleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private String title;
    @Enumerated(EnumType.STRING)
    private RecurrenceType recurrenceType;
    private Integer dayOfMonth;
    private LocalDate startDate;
    private LocalDate endDate;
    private long amount;
    @Enumerated(EnumType.STRING)
    private EventType eventType;
    @Enumerated(EnumType.STRING)
    private Direction direction;
    private boolean active;

    protected RecurringRuleEntity() {
    }

    public RecurringRuleEntity(Long userId, String title, RecurrenceType recurrenceType, Integer dayOfMonth, LocalDate startDate, LocalDate endDate, long amount, EventType eventType, Direction direction, boolean active) {
        this.userId = userId;
        this.title = title;
        this.recurrenceType = recurrenceType;
        this.dayOfMonth = dayOfMonth;
        this.startDate = startDate;
        this.endDate = endDate;
        this.amount = amount;
        this.eventType = eventType;
        this.direction = direction;
        this.active = active;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getTitle() { return title; }
    public RecurrenceType getRecurrenceType() { return recurrenceType; }
    public Integer getDayOfMonth() { return dayOfMonth; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public long getAmount() { return amount; }
    public EventType getEventType() { return eventType; }
    public Direction getDirection() { return direction; }
    public boolean isActive() { return active; }

    public void update(String title, RecurrenceType recurrenceType, Integer dayOfMonth, LocalDate startDate, LocalDate endDate, Long amount, EventType eventType, Direction direction, Boolean active) {
        if (title != null) this.title = title;
        if (recurrenceType != null) this.recurrenceType = recurrenceType;
        if (dayOfMonth != null) this.dayOfMonth = dayOfMonth;
        if (startDate != null) this.startDate = startDate;
        if (endDate != null) this.endDate = endDate;
        if (amount != null) this.amount = amount;
        if (eventType != null) this.eventType = eventType;
        if (direction != null) this.direction = direction;
        if (active != null) this.active = active;
    }
}
