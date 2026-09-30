package com.moneyweather.domain.entity;

import com.moneyweather.domain.Enums.CategoryType;
import jakarta.persistence.*;

@Entity
@Table(name = "categories")
public class CategoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private String name;
    @Enumerated(EnumType.STRING)
    private CategoryType categoryType;
    private boolean systemDefault;

    protected CategoryEntity() {
    }

    public CategoryEntity(Long userId, String name, CategoryType categoryType, boolean systemDefault) {
        this.userId = userId;
        this.name = name;
        this.categoryType = categoryType;
        this.systemDefault = systemDefault;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public CategoryType getCategoryType() { return categoryType; }
    public boolean isSystemDefault() { return systemDefault; }

    public void update(String name, CategoryType categoryType) {
        if (name != null) this.name = name;
        if (categoryType != null) this.categoryType = categoryType;
    }
}
