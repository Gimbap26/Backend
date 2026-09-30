package com.moneyweather.api;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시뮬레이션과 알림.
 * 시드 9월 예정분을 반영한 잔액 흐름: 1,200,000 → 9/5 680,000 → 9/10 600,000 → 9/15 561,000(최저) → 9/25 3,061,000.
 */
class SimulationAndAlertTest extends ApiTestSupport {

    private String simulate(String spendingDate, long amount) throws Exception {
        return body(postJson("/api/v1/available-funds/simulations", """
                {"spendingDate":"%s","amount":%d,"targetDate":"2026-09-30","title":"에어팟"}
                """.formatted(spendingDate, amount)).andExpect(status().isOk()));
    }

    // --- 시뮬레이션 -----------------------------------------------------------

    @Test
    void spendingDateChangesTheLowestBalance() throws Exception {
        // 급여일 전에 쓰면 가장 빠듯한 9/15 잔액이 그만큼 줄어든다
        String early = simulate("2026-09-12", 250_000);
        assertThat(readLong(early, "$.before.minimumBalance")).isEqualTo(561_000);
        assertThat(readLong(early, "$.after.minimumBalance")).isEqualTo(311_000);
        assertThat((String) read(early, "$.after.minimumBalanceDate")).isEqualTo("2026-09-15");

        // 급여일 뒤에 쓰면 가장 빠듯한 날은 영향을 받지 않는다
        String late = simulate("2026-09-28", 250_000);
        assertThat(readLong(late, "$.after.minimumBalance")).isEqualTo(561_000);

        // 이번 달 쓸 수 있는 돈은 언제 쓰든 같은 만큼 준다
        assertThat(readLong(early, "$.after.availableAmount")).isEqualTo(311_000);
        assertThat(readLong(late, "$.after.availableAmount")).isEqualTo(311_000);
    }

    @Test
    void simulationExplainsTheChangeWithTitle() throws Exception {
        String result = simulate("2026-09-12", 250_000);
        assertThat((String) read(result, "$.before.weather")).isEqualTo("CLOUDY");
        assertThat((String) read(result, "$.after.weather")).isEqualTo("CLOUDY");
        assertThat((String) read(result, "$.message")).contains("에어팟").contains("250,000원")
                .contains("흐림 → 흐림").doesNotContain("CLOUDY");
    }

    @Test
    void simulationDoesNotSaveAnything() throws Exception {
        simulate("2026-09-12", 250_000);
        List<Object> events = read(getOk("/api/v1/financial-events", "from", "2026-09-01", "to", "2026-09-30"), "$.events");
        assertThat(events).hasSize(4);
    }

    @Test
    void spendingAfterTargetDateIsRejected() throws Exception {
        postJson("/api/v1/available-funds/simulations", """
                {"spendingDate":"2026-10-05","amount":1000,"targetDate":"2026-09-30"}
                """).andExpect(status().isBadRequest());
    }

    // --- 알림 ---------------------------------------------------------------

    private List<String> alertTypes() throws Exception {
        return read(getOk("/api/v1/forecast-alerts"), "$.alerts[*].type");
    }

    @Test
    void healthySeedHasNoBalanceOrBudgetAlerts() throws Exception {
        // 다가오는 결제 알림은 실행하는 날짜에 따라 달라지므로 여기서는 보지 않는다
        assertThat(alertTypes()).doesNotContain("NEGATIVE_BALANCE", "LOW_BALANCE", "BUDGET_EXCEEDED", "BUDGET_NEAR_LIMIT");
    }

    @Test
    void lowBalanceIsFlaggedOnItsDate() throws Exception {
        // 자산 합계 700,000 → 9/15 최저 61,000
        patchJson("/api/v1/accounts/" + accountId("생활비 통장"), "{\"balance\":300000}").andExpect(status().isOk());

        String alerts = getOk("/api/v1/forecast-alerts");
        assertThat((String) first(alerts, "$.alerts[?(@.type=='LOW_BALANCE')].date")).isEqualTo("2026-09-15");
        assertThat(alertTypes()).doesNotContain("NEGATIVE_BALANCE");
    }

    @Test
    void negativeBalanceIsFlaggedOnTheFirstDayItHappens() throws Exception {
        // 자산 합계 500,000 → 9/5 카드 대금 520,000 이 빠지면 -20,000
        patchJson("/api/v1/accounts/" + accountId("생활비 통장"), "{\"balance\":100000}").andExpect(status().isOk());

        String alerts = getOk("/api/v1/forecast-alerts");
        assertThat((String) first(alerts, "$.alerts[?(@.type=='NEGATIVE_BALANCE')].date")).isEqualTo("2026-09-05");
        assertThat((String) first(alerts, "$.alerts[?(@.type=='NEGATIVE_BALANCE')].severity")).isEqualTo("DANGER");
        assertThat(alertTypes()).doesNotContain("LOW_BALANCE");
    }

    @Test
    void budgetExceededAndNearLimitAreFlagged() throws Exception {
        // 식비/카페: 6,500 + 400,000 = 406,500 > 한도 350,000
        postJson("/api/v1/transactions", """
                {"transactionDate":"2026-09-20","merchant":"회식","amount":400000,"transactionType":"EXPENSE","categoryId":%d}
                """.formatted(categoryId("식비/카페"))).andExpect(status().isOk());
        // 교통: 1,500 + 80,000 = 81,500 ≥ 한도 100,000 의 80%
        postJson("/api/v1/transactions", """
                {"transactionDate":"2026-09-21","merchant":"택시","amount":80000,"transactionType":"EXPENSE","categoryId":%d}
                """.formatted(categoryId("교통"))).andExpect(status().isOk());

        String alerts = getOk("/api/v1/forecast-alerts");
        assertThat(((Number) first(alerts, "$.alerts[?(@.type=='BUDGET_EXCEEDED')].amount")).longValue()).isEqualTo(56_500);
        assertThat((String) first(alerts, "$.alerts[?(@.type=='BUDGET_NEAR_LIMIT')].title")).contains("교통");
    }

    @Test
    void upcomingBillWithinAWeekIsFlagged() throws Exception {
        LocalDate soon = LocalDate.now().plusDays(3);
        postJson("/api/v1/financial-events", """
                {"eventDate":"%s","title":"관리비","amount":150000,"direction":"OUTFLOW","eventType":"ETC","fixed":true}
                """.formatted(soon)).andExpect(status().isOk());

        String alerts = getOk("/api/v1/forecast-alerts");
        assertThat((String) first(alerts, "$.alerts[?(@.type=='UPCOMING_BILL')].date")).isEqualTo(soon.toString());
    }

    @Test
    void alertsAreOrderedBySeverity() throws Exception {
        patchJson("/api/v1/accounts/" + accountId("생활비 통장"), "{\"balance\":100000}").andExpect(status().isOk());
        postJson("/api/v1/transactions", """
                {"transactionDate":"2026-09-21","merchant":"택시","amount":80000,"transactionType":"EXPENSE","categoryId":%d}
                """.formatted(categoryId("교통"))).andExpect(status().isOk());

        List<String> severities = read(getOk("/api/v1/forecast-alerts"), "$.alerts[*].severity");
        assertThat(severities).startsWith("DANGER");
        assertThat(severities.indexOf("CAUTION")).isGreaterThan(severities.lastIndexOf("DANGER"));
    }
}
