package com.moneyweather.service;

import com.moneyweather.domain.Enums.*;
import com.moneyweather.domain.entity.AccountEntity;
import com.moneyweather.domain.entity.FinancialEventEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;

@Service
public class ForecastService {
    public long currentBalance(List<AccountEntity> accounts) {
        return accounts.stream().filter(AccountEntity::isIncludedInAssets).mapToLong(AccountEntity::getBalance).sum();
    }

    /** 아직 지불되지 않은(SCHEDULED) 고정 지출 합계. 결제 완료분은 이미 잔액에서 빠졌으므로 제외한다. */
    public long fixedOutflows(List<FinancialEventEntity> events) {
        return events.stream()
                .filter(e -> e.isFixed() && e.getDirection() == Direction.OUTFLOW && isPending(e))
                .mapToLong(FinancialEventEntity::getAmount)
                .sum();
    }

    /** 잔액에 아직 반영되지 않은 예정 이벤트인지. 취소분과 결제 완료분은 앞으로의 현금 흐름이 아니다. */
    public boolean isPending(FinancialEventEntity event) {
        return event.getStatus() == EventStatus.SCHEDULED;
    }

    public WeatherStatus weather(long amount) {
        if (amount < 100_000) return WeatherStatus.STORM;
        if (amount < 300_000) return WeatherStatus.RAINY;
        if (amount < 700_000) return WeatherStatus.CLOUDY;
        return WeatherStatus.SUNNY;
    }

    public Map<String, Object> risk(long available) {
        RiskLevel level = available < 200_000 ? RiskLevel.DANGER : available < 500_000 ? RiskLevel.CAUTION : RiskLevel.GOOD;
        String message = switch (level) {
            case GOOD -> "이번 달 자금 흐름이 안정적입니다.";
            case CAUTION -> "예정 지출 전 생활비를 확인하세요.";
            case DANGER -> "생활비 부족 위험이 큽니다.";
        };
        return Map.of("level", level, "message", message);
    }

    public Map<String, Object> forecast(LocalDate from, LocalDate to, long startingBalance, List<FinancialEventEntity> events) {
        validateRange(from, to);
        List<Map<String, Object>> timeline = new ArrayList<>();
        long balance = startingBalance;
        long min = balance;
        LocalDate minDate = from;
        Map<LocalDate, List<FinancialEventEntity>> byDate = new HashMap<>();
        for (FinancialEventEntity event : events) {
            if (isPending(event)) {
                byDate.computeIfAbsent(event.getEventDate(), ignored -> new ArrayList<>()).add(event);
            }
        }

        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            List<FinancialEventEntity> dayEvents = byDate.getOrDefault(date, List.of());
            long delta = dayEvents.stream().mapToLong(e -> e.getDirection() == Direction.INFLOW ? e.getAmount() : -e.getAmount()).sum();
            balance += delta;
            if (balance < min) {
                min = balance;
                minDate = date;
            }
            if (!dayEvents.isEmpty()) {
                String title = dayEvents.size() == 1 ? dayEvents.getFirst().getTitle() : dayEvents.size() + " scheduled events";
                timeline.add(Map.of("date", date, "expectedBalance", balance, "eventTitle", title, "eventAmount", delta));
            }
        }

        return Map.of(
                "from", from,
                "to", to,
                "weatherStatus", weather(min),
                "minimumExpectedBalance", min,
                "minimumBalanceDate", minDate,
                "timeline", timeline
        );
    }

    public void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be on or before to.");
        }
    }
}
