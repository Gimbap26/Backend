package com.moneyweather.repository;

import com.moneyweather.domain.entity.MonthlyBudgetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MonthlyBudgetRepository extends JpaRepository<MonthlyBudgetEntity, Long> {
    Optional<MonthlyBudgetEntity> findByUserIdAndBudgetMonth(Long userId, String budgetMonth);
    Optional<MonthlyBudgetEntity> findFirstByUserIdOrderByBudgetMonthDesc(Long userId);
}
