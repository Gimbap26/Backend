package com.moneyweather.repository;

import com.moneyweather.domain.entity.FinancialEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface FinancialEventRepository extends JpaRepository<FinancialEventEntity, Long> {
    List<FinancialEventEntity> findByUserIdAndEventDateBetween(Long userId, LocalDate from, LocalDate to);
    List<FinancialEventEntity> findByUserId(Long userId);
}
