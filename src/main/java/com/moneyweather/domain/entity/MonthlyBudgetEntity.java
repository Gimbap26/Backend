package com.moneyweather.domain.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "monthly_budgets")
public class MonthlyBudgetEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private String budgetMonth;
    private int totalLimitAmount;

    protected MonthlyBudgetEntity() {
    }

    public MonthlyBudgetEntity(Long userId, String budgetMonth, int totalLimitAmount) {
        this.userId = userId;
        this.budgetMonth = budgetMonth;
        this.totalLimitAmount = totalLimitAmount;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getBudgetMonth() { return budgetMonth; }
    public int getTotalLimitAmount() { return totalLimitAmount; }
    public void update(String budgetMonth, int totalLimitAmount) {
        this.budgetMonth = budgetMonth;
        this.totalLimitAmount = totalLimitAmount;
    }
}
