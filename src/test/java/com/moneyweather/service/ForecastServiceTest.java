package com.moneyweather.service;

import com.moneyweather.domain.Enums.*;
import com.moneyweather.domain.entity.AccountEntity;
import com.moneyweather.domain.entity.FinancialEventEntity;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ForecastServiceTest {
    private final ForecastService service = new ForecastService();

    @Test
    void currentBalanceOnlyIncludesAssetAccounts() {
        long balance = service.currentBalance(List.of(
                new AccountEntity(1L, "A", "생활비", 800_000, "생활비", true),
                new AccountEntity(1L, "B", "여행 적금", 1_000_000, "목적자금", false),
                new AccountEntity(1L, "C", "비상금", 400_000, "예비비", true)
        ));

        assertThat(balance).isEqualTo(1_200_000);
    }

    @Test
    void fixedOutflowsIgnoreCanceledAndInflowEvents() {
        long fixedOutflows = service.fixedOutflows(List.of(
                event(520_000, Direction.OUTFLOW, EventStatus.SCHEDULED, true),
                event(80_000, Direction.OUTFLOW, EventStatus.CANCELED, true),
                event(2_500_000, Direction.INFLOW, EventStatus.SCHEDULED, true),
                event(30_000, Direction.OUTFLOW, EventStatus.SCHEDULED, false)
        ));

        assertThat(fixedOutflows).isEqualTo(520_000);
    }

    @Test
    void weatherThresholdsAreStable() {
        assertThat(service.weather(99_999)).isEqualTo(WeatherStatus.STORM);
        assertThat(service.weather(100_000)).isEqualTo(WeatherStatus.RAINY);
        assertThat(service.weather(300_000)).isEqualTo(WeatherStatus.CLOUDY);
        assertThat(service.weather(700_000)).isEqualTo(WeatherStatus.SUNNY);
    }

    @Test
    void forecastFindsMinimumBalanceDate() {
        Map<String, Object> result = service.forecast(
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
                1_200_000,
                List.of(
                        new FinancialEventEntity(1L, LocalDate.of(2026, 9, 5), "카드 결제", 520_000, Direction.OUTFLOW, EventType.CARD_BILL, EventStatus.SCHEDULED, true),
                        new FinancialEventEntity(1L, LocalDate.of(2026, 9, 10), "통신비", 80_000, Direction.OUTFLOW, EventType.TELECOM, EventStatus.SCHEDULED, true),
                        new FinancialEventEntity(1L, LocalDate.of(2026, 9, 25), "급여", 2_500_000, Direction.INFLOW, EventType.SALARY, EventStatus.SCHEDULED, true)
                )
        );

        assertThat(result.get("minimumExpectedBalance")).isEqualTo(600_000L);
        assertThat(result.get("minimumBalanceDate")).isEqualTo(LocalDate.of(2026, 9, 10));
        assertThat(result.get("weatherStatus")).isEqualTo(WeatherStatus.CLOUDY);
    }

    @Test
    void invalidRangeThrowsBadRequest() {
        assertThatThrownBy(() -> service.validateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private FinancialEventEntity event(long amount, Direction direction, EventStatus status, boolean fixed) {
        return new FinancialEventEntity(1L, LocalDate.of(2026, 9, 1), "event", amount, direction, EventType.ETC, status, fixed);
    }
}
