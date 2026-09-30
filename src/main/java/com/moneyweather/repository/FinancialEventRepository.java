package com.moneyweather.repository;

import com.moneyweather.domain.entity.FinancialEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import com.moneyweather.domain.Enums.EventStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FinancialEventRepository extends JpaRepository<FinancialEventEntity, Long> {
    List<FinancialEventEntity> findByUserIdAndEventDateBetween(Long userId, LocalDate from, LocalDate to);
    List<FinancialEventEntity> findByUserId(Long userId);
    boolean existsByAccountId(Long accountId);

    /** 반복 규칙이 해당 기간에 이미 만들어 둔 이벤트. lazy 생성의 중복 판별에 쓴다. */
    List<FinancialEventEntity> findByRecurringRuleIdAndEventDateBetween(Long recurringRuleId, LocalDate from, LocalDate to);

    /** 카드 청구 이벤트는 (카드, 결제일) 조합으로 유일하다. */
    Optional<FinancialEventEntity> findByCardIdAndEventDate(Long cardId, LocalDate eventDate);

    /** 규칙 수정/비활성화 시 아직 지나지 않은 예정 이벤트만 걷어낸다. */
    void deleteByRecurringRuleIdAndEventDateGreaterThanEqualAndStatus(Long recurringRuleId, LocalDate from, EventStatus status);
}
