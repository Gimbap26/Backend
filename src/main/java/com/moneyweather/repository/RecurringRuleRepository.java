package com.moneyweather.repository;

import com.moneyweather.domain.entity.RecurringRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecurringRuleRepository extends JpaRepository<RecurringRuleEntity, Long> {
    List<RecurringRuleEntity> findByUserId(Long userId);
    boolean existsByAccountId(Long accountId);
}
