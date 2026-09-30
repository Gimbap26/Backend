package com.moneyweather.service;

import com.moneyweather.domain.Enums.*;
import com.moneyweather.domain.entity.AccountEntity;
import com.moneyweather.domain.entity.FinancialEventEntity;
import com.moneyweather.domain.entity.UserEntity;
import com.moneyweather.repository.AccountRepository;
import com.moneyweather.repository.FinancialEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * 자금 날씨 계산: 대시보드, 잔액 예측, 지출 시뮬레이션.
 *
 * <pre>사용 가능 자금 = 현재 잔액(자산 포함 계좌) − 기간 내 아직 내지 않은 고정 지출</pre>
 * 순수 계산은 {@link ForecastService}에 있고, 여기서는 사용자 데이터를 모아 넘긴다.
 * 조회 전에 기간의 반복·카드 이벤트를 채우므로({@link ScheduleService#materializeRange}) 쓰기 트랜잭션으로 돈다.
 */
@Service
@Transactional
public class MoneyWeatherService {
    private final AccountRepository accounts;
    private final FinancialEventRepository events;
    private final ScheduleService schedule;
    private final ForecastService forecastService;
    private final CurrentUserService currentUser;

    public MoneyWeatherService(AccountRepository accounts, FinancialEventRepository events, ScheduleService schedule,
                               ForecastService forecastService, CurrentUserService currentUser) {
        this.accounts = accounts;
        this.events = events;
        this.schedule = schedule;
        this.forecastService = forecastService;
        this.currentUser = currentUser;
    }

    public record SimulationRequest(LocalDate spendingDate, long amount, LocalDate targetDate, String title) {}

    public Map<String, Object> forecast(LocalDate from, LocalDate to) {
        forecastService.validateRange(from, to);
        Long userId = currentUser.requireId();
        schedule.materializeRange(userId, from, to);
        long currentBalance = forecastService.currentBalance(accounts.findByUserId(userId));
        return forecastService.forecast(from, to, currentBalance, events.findByUserIdAndEventDateBetween(userId, from, to));
    }

    /** 기간을 생략하면 사용자의 기준 월 전체. */
    public Map<String, Object> dashboard(LocalDate baseDate, LocalDate targetDate) {
        UserEntity user = currentUser.require();
        LocalDate base = baseDate == null ? YearMonth.parse(user.getBaseMonth()).atDay(1) : baseDate;
        LocalDate target = targetDate == null ? base.withDayOfMonth(base.lengthOfMonth()) : targetDate;
        schedule.materializeRange(user.getId(), base, target);
        List<AccountEntity> accountRows = accounts.findByUserId(user.getId());
        List<FinancialEventEntity> eventRows = events.findByUserIdAndEventDateBetween(user.getId(), base, target);
        long currentBalance = forecastService.currentBalance(accountRows);
        long fixedOutflows = forecastService.fixedOutflows(eventRows);
        long available = currentBalance - fixedOutflows;
        return Map.of("currentBalance", currentBalance, "fixedOutflows", fixedOutflows, "availableAmount", available,
                "weather", forecastService.weather(available), "riskSummary", forecastService.risk(available),
                "nextEvents", upcoming(eventRows, base, target));
    }

    /**
     * {@code spendingDate}에 가상의 지출을 넣었을 때를 계산한다. 저장하지 않는다.
     * 기간은 지출일이 속한 달의 1일부터 {@code targetDate}까지로, 대시보드와 같은 기준을 쓴다.
     * 사용 가능 자금은 날짜와 상관없이 줄지만, 최저 잔액과 그 날짜는 지출일에 따라 달라진다.
     */
    public Map<String, Object> simulate(SimulationRequest request) {
        if (request.spendingDate().isAfter(request.targetDate())) {
            throw Errors.badRequest("spendingDate must be on or before targetDate.");
        }
        Long userId = currentUser.requireId();
        LocalDate from = YearMonth.from(request.spendingDate()).atDay(1);
        LocalDate to = request.targetDate();
        schedule.materializeRange(userId, from, to);

        long currentBalance = forecastService.currentBalance(accounts.findByUserId(userId));
        List<FinancialEventEntity> scheduled = events.findByUserIdAndEventDateBetween(userId, from, to);
        long beforeAvailable = currentBalance - forecastService.fixedOutflows(scheduled);
        long afterAvailable = beforeAvailable - request.amount();

        String title = request.title() == null || request.title().isBlank() ? "추가 지출" : request.title();
        List<FinancialEventEntity> withSpending = new ArrayList<>(scheduled);
        withSpending.add(new FinancialEventEntity(userId, request.spendingDate(), title, request.amount(),
                Direction.OUTFLOW, EventType.ETC, EventStatus.SCHEDULED, false));
        Map<String, Object> beforeForecast = forecastService.forecast(from, to, currentBalance, scheduled);
        Map<String, Object> afterForecast = forecastService.forecast(from, to, currentBalance, withSpending);

        WeatherStatus beforeWeather = forecastService.weather(beforeAvailable);
        WeatherStatus afterWeather = forecastService.weather(afterAvailable);
        long afterMin = ((Number) afterForecast.get("minimumExpectedBalance")).longValue();
        String message = "%s에 %s(%,d원)을 쓰면 %s까지 쓸 수 있는 돈이 %,d원에서 %,d원으로 줄고, 날씨는 %s → %s입니다. 가장 잔액이 적은 날은 %s(%,d원)입니다."
                .formatted(request.spendingDate(), title, request.amount(), to, beforeAvailable, afterAvailable,
                        beforeWeather.label(), afterWeather.label(), afterForecast.get("minimumBalanceDate"), afterMin);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("spendingDate", request.spendingDate());
        body.put("targetDate", to);
        body.put("before", simulationSide(beforeAvailable, beforeWeather, beforeForecast));
        body.put("after", simulationSide(afterAvailable, afterWeather, afterForecast));
        body.put("difference", -request.amount());
        body.put("message", message);
        return body;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> weatherStatuses() {
        return Map.of("statuses", List.of(
                Map.of("weather", WeatherStatus.SUNNY, "label", WeatherStatus.SUNNY.label(), "description", "여유 자금이 충분합니다."),
                Map.of("weather", WeatherStatus.CLOUDY, "label", WeatherStatus.CLOUDY.label(), "description", "예정 지출을 확인하세요."),
                Map.of("weather", WeatherStatus.RAINY, "label", WeatherStatus.RAINY.label(), "description", "추가 소비를 줄이는 것이 좋습니다."),
                Map.of("weather", WeatherStatus.STORM, "label", WeatherStatus.STORM.label(), "description", "생활비 부족 위험이 큽니다.")
        ));
    }

    private Map<String, Object> simulationSide(long available, WeatherStatus weather, Map<String, Object> forecast) {
        Map<String, Object> side = new LinkedHashMap<>();
        side.put("availableAmount", available);
        side.put("weather", weather);
        side.put("minimumBalance", forecast.get("minimumExpectedBalance"));
        side.put("minimumBalanceDate", forecast.get("minimumBalanceDate"));
        side.put("forecastWeather", forecast.get("weatherStatus"));
        return side;
    }

    /** 기간 안의 아직 내지 않은 이벤트를 날짜순으로 5개까지. */
    private List<Map<String, Object>> upcoming(List<FinancialEventEntity> eventRows, LocalDate from, LocalDate to) {
        return eventRows.stream()
                .filter(e -> !e.getEventDate().isBefore(from) && !e.getEventDate().isAfter(to) && forecastService.isPending(e))
                .sorted(Comparator.comparing(FinancialEventEntity::getEventDate))
                .limit(5)
                .map(e -> Map.<String, Object>of("date", e.getEventDate(), "title", e.getTitle(), "amount", e.getAmount(), "direction", e.getDirection()))
                .toList();
    }
}
