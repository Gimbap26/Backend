package com.moneyweather.service;

import com.moneyweather.domain.Enums.Direction;
import com.moneyweather.domain.Enums.EventStatus;
import com.moneyweather.domain.Enums.EventType;
import com.moneyweather.domain.entity.UserEntity;
import com.moneyweather.service.ScheduleService.FinancialEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/** 예측 잔액, 다가오는 결제, 예산 집행 상황을 보고 사용자에게 알릴 것을 골라낸다. */
@Service
@Transactional
public class AlertService {
    /** 이 금액 아래로 잔액이 떨어질 것으로 예측되면 주의를 준다. */
    static final long LOW_BALANCE_THRESHOLD = 300_000;
    /** 오늘부터 며칠 안의 결제를 '다가오는 결제'로 볼지. */
    static final int UPCOMING_DAYS = 7;
    /** 예산의 몇 %를 쓰면 한도 임박으로 볼지. */
    static final double BUDGET_NEAR_RATIO = 0.8;

    private final MoneyWeatherService moneyWeather;
    private final ScheduleService schedule;
    private final BudgetService budgets;
    private final CurrentUserService currentUser;

    public AlertService(MoneyWeatherService moneyWeather, ScheduleService schedule, BudgetService budgets, CurrentUserService currentUser) {
        this.moneyWeather = moneyWeather;
        this.schedule = schedule;
        this.budgets = budgets;
        this.currentUser = currentUser;
    }

    public enum Severity { DANGER, CAUTION, INFO }

    public record Alert(String type, Severity severity, LocalDate date, String title, String message, Long amount) {}

    /** 기간을 생략하면 사용자의 기준 월 전체를 본다. */
    public Map<String, Object> alerts(LocalDate from, LocalDate to) {
        UserEntity user = currentUser.require();
        YearMonth baseMonth = YearMonth.parse(user.getBaseMonth());
        LocalDate start = from != null ? from : baseMonth.atDay(1);
        LocalDate end = to != null ? to : YearMonth.from(start).atEndOfMonth();

        List<Alert> alerts = new ArrayList<>();
        alerts.addAll(balanceAlerts(start, end));
        alerts.addAll(upcomingBillAlerts());
        alerts.addAll(budgetAlerts(YearMonth.from(start)));
        alerts.sort(Comparator.comparing(Alert::severity).thenComparing(Alert::date, Comparator.nullsLast(Comparator.naturalOrder())));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", start);
        body.put("to", end);
        body.put("count", alerts.size());
        body.put("alerts", alerts);
        return body;
    }

    /** 예측 잔액이 마이너스가 되는 첫날, 또는 최저 잔액이 기준 아래로 내려가는 날. */
    private List<Alert> balanceAlerts(LocalDate from, LocalDate to) {
        Map<String, Object> forecast = moneyWeather.forecast(from, to);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> timeline = (List<Map<String, Object>>) forecast.get("timeline");
        for (Map<String, Object> point : timeline) {
            long balance = ((Number) point.get("expectedBalance")).longValue();
            if (balance < 0) {
                LocalDate date = (LocalDate) point.get("date");
                return List.of(new Alert("NEGATIVE_BALANCE", Severity.DANGER, date, "잔액 부족 예상",
                        "%s에 잔액이 %,d원으로 마이너스가 될 것으로 예상됩니다. 그 전에 지출을 줄이거나 입금 일정을 확인하세요.".formatted(date, balance),
                        balance));
            }
        }
        long min = ((Number) forecast.get("minimumExpectedBalance")).longValue();
        if (min < LOW_BALANCE_THRESHOLD) {
            LocalDate date = (LocalDate) forecast.get("minimumBalanceDate");
            return List.of(new Alert("LOW_BALANCE", Severity.CAUTION, date, "잔액 낮음 예상",
                    "%s에 잔액이 %,d원까지 내려갈 것으로 예상됩니다.".formatted(date, min), min));
        }
        return List.of();
    }

    /** 오늘부터 일주일 안에 빠져나갈 카드 대금과 고정 지출. */
    private List<Alert> upcomingBillAlerts() {
        LocalDate today = LocalDate.now();
        LocalDate until = today.plusDays(UPCOMING_DAYS);
        @SuppressWarnings("unchecked")
        List<FinancialEvent> upcoming = (List<FinancialEvent>) schedule.events(today, until, Direction.OUTFLOW, null, EventStatus.SCHEDULED).get("events");
        return upcoming.stream()
                .filter(e -> e.fixed() || e.eventType() == EventType.CARD_BILL)
                .map(e -> new Alert("UPCOMING_BILL", Severity.INFO, e.eventDate(), e.title() + " 결제 예정",
                        "%s에 %s %,d원이 빠져나갑니다.".formatted(e.eventDate(), e.title(), e.amount()), e.amount()))
                .toList();
    }

    /**
     * 카테고리별 예산 초과와 한도 임박. 해당 월 예산이 없으면 알릴 것이 없다.
     * 예산 유무를 먼저 확인한다({@link BudgetService#exists} 참고).
     */
    private List<Alert> budgetAlerts(YearMonth month) {
        if (!budgets.exists(month)) return List.of();
        Map<String, Object> status = budgets.status(month);
        List<Alert> alerts = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categories = (List<Map<String, Object>>) status.get("categories");
        for (Map<String, Object> row : categories) {
            Object limitValue = row.get("limit");
            if (limitValue == null) continue;
            long limit = ((Number) limitValue).longValue();
            long spent = ((Number) row.get("spent")).longValue();
            String category = (String) row.get("category");
            if (spent > limit) {
                alerts.add(new Alert("BUDGET_EXCEEDED", Severity.DANGER, null, category + " 예산 초과",
                        "%s 예산 %,d원 중 %,d원을 써서 %,d원 초과했습니다.".formatted(category, limit, spent, spent - limit), spent - limit));
            } else if (limit > 0 && spent >= limit * BUDGET_NEAR_RATIO) {
                alerts.add(new Alert("BUDGET_NEAR_LIMIT", Severity.CAUTION, null, category + " 예산 임박",
                        "%s 예산의 %d%%를 썼습니다. 남은 한도는 %,d원입니다.".formatted(category, spent * 100 / limit, limit - spent), limit - spent));
            }
        }
        return alerts;
    }
}
