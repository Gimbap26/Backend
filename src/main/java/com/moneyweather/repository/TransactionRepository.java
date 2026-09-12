package com.moneyweather.repository;

import com.moneyweather.domain.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {
    List<TransactionEntity> findByUserIdAndTransactionDateBetween(Long userId, LocalDate from, LocalDate to);
}
