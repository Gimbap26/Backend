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

    /** 예산은 카테고리를 이름으로 참조하므로 카테고리 이름이 바뀌면 함께 바꿔야 한다. */
    public void renameCategory(String categoryName) {
        this.categoryName = categoryName;
    }
}
