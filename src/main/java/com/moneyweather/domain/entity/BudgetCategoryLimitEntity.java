package com.moneyweather.domain.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "budget_category_limits")
public class BudgetCategoryLimitEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long budgetId;
    private String categoryName;
    private int limitAmount;

    protected BudgetCategoryLimitEntity() {
    }

    public BudgetCategoryLimitEntity(Long budgetId, String categoryName, int limitAmount) {
        this.budgetId = budgetId;
        this.categoryName = categoryName;
        this.limitAmount = limitAmount;
    }

    public Long getId() { return id; }
    public Long getBudgetId() { return budgetId; }
    public String getCategoryName() { return categoryName; }
    public int getLimitAmount() { return limitAmount; }
}
