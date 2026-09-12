package com.moneyweather.repository;

import com.moneyweather.domain.entity.BudgetCategoryLimitEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BudgetCategoryLimitRepository extends JpaRepository<BudgetCategoryLimitEntity, Long> {
    List<BudgetCategoryLimitEntity> findByBudgetId(Long budgetId);
    void deleteByBudgetId(Long budgetId);
}
