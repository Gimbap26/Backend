package com.moneyweather.repository;

import com.moneyweather.domain.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;

public interface TransactionRepository extends JpaRepository<TransactionEntity, Long>, JpaSpecificationExecutor<TransactionEntity> {
    List<TransactionEntity> findByUserIdAndTransactionDateBetween(Long userId, LocalDate from, LocalDate to);

    /** 카드 청구액 산정용. 전월 카드 사용분을 합산할 때 쓴다. */
    List<TransactionEntity> findByUserIdAndCardIdAndTransactionDateBetween(Long userId, Long cardId, LocalDate from, LocalDate to);

    List<TransactionEntity> findByCategoryId(Long categoryId);
    boolean existsByAccountIdOrTransferAccountId(Long accountId, Long transferAccountId);
}
