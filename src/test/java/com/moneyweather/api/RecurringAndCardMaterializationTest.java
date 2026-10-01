package com.moneyweather.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 반복 규칙 lazy 생성, 카드 결제일 연동, 거래 CRUD, 예산 대조의 동작을 검증한다.
 * H2 메모리 DB를 다른 테스트 클래스와 공유하므로, 각 테스트 전후로 시드를 초기화해
 * 실행 순서에 상관없이 서로 간섭하지 않게 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(DemoUserTokenConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RecurringAndCardMaterializationTest {
    @Autowired
    MockMvc mockMvc;

    @BeforeEach
    void reseed() throws Exception {
        seed();
    }

    @AfterAll
    void restoreSeedForOtherTests() throws Exception {
        seed();
    }

    private void seed() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed")
                        .contentType("application/json")
                        .content("{\"userId\":1,\"baseMonth\":\"2026-09\",\"reset\":true}"))
                .andExpect(status().isOk());
    }

    private String eventsBetween(String from, String to) throws Exception {
        return mockMvc.perform(get("/api/v1/financial-events").param("from", from).param("to", to))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private List<Object> eventsTitled(String body, String title) {
        return JsonPath.read(body, "$.events[?(@.title=='" + title + "')]");
    }

    // --- 반복 규칙 -------------------------------------------------------

    @Test
    void recurringRuleGeneratesEventsInFutureMonths() throws Exception {
        // 시드의 통신비 규칙은 2026-09-01 시작, 매달 10일, 종료일 없음.
        // 기존 구현은 시작 월 1건만 만들었기 때문에 11월에는 아무것도 없었다.
        List<Object> november = eventsTitled(eventsBetween("2026-11-01", "2026-11-30"), "통신비");
        assertThat(november).hasSize(1);

        List<Object> december = eventsTitled(eventsBetween("2026-12-01", "2026-12-31"), "통신비");
        assertThat(december).hasSize(1);
    }

    @Test
    void recurringRuleRespectsEndDate() throws Exception {
        mockMvc.perform(post("/api/v1/recurring-rules")
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "헬스장",
                                  "recurrenceType": "MONTHLY",
                                  "dayOfMonth": 12,
                                  "startDate": "2026-10-01",
                                  "endDate": "2026-11-30",
                                  "amount": 50000,
                                  "eventType": "SUBSCRIPTION",
                                  "direction": "OUTFLOW"
                                }
                                """))
                .andExpect(status().isOk());

        assertThat(eventsTitled(eventsBetween("2026-11-01", "2026-11-30"), "헬스장")).hasSize(1);
        assertThat(eventsTitled(eventsBetween("2026-12-01", "2026-12-31"), "헬스장")).isEmpty();
    }

    @Test
    void weeklyRuleGeneratesMultipleEventsPerMonth() throws Exception {
        // WEEKLY 는 기존 구현에서 조용히 무시돼 이벤트가 하나도 생기지 않았다.
        mockMvc.perform(post("/api/v1/recurring-rules")
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "주간저축",
                                  "recurrenceType": "WEEKLY",
                                  "startDate": "2026-10-05",
                                  "amount": 30000,
                                  "eventType": "TRANSFER",
                                  "direction": "OUTFLOW"
                                }
                                """))
                .andExpect(status().isOk());

        // 2026-10-05 부터 7일 간격 -> 5, 12, 19, 26 네 번
        assertThat(eventsTitled(eventsBetween("2026-10-01", "2026-10-31"), "주간저축")).hasSize(4);
    }

    @Test
    void monthlyRuleRequiresDayOfMonth() throws Exception {
        mockMvc.perform(post("/api/v1/recurring-rules")
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "날짜 없는 월반복",
                                  "recurrenceType": "MONTHLY",
                                  "startDate": "2026-10-01",
                                  "amount": 50000,
                                  "eventType": "SUBSCRIPTION",
                                  "direction": "OUTFLOW"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatingRuleToMonthlyRequiresDayOfMonth() throws Exception {
        String created = mockMvc.perform(post("/api/v1/recurring-rules")
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "주간저축",
                                  "recurrenceType": "WEEKLY",
                                  "startDate": "2026-10-05",
                                  "amount": 30000,
                                  "eventType": "TRANSFER",
                                  "direction": "OUTFLOW"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Object id = JsonPath.read(created, "$.recurringRuleId");

        mockMvc.perform(patch("/api/v1/recurring-rules/{id}", id)
                        .contentType("application/json")
                        .content("{\"recurrenceType\":\"MONTHLY\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void repeatedQueryDoesNotDuplicateEvents() throws Exception {
        int first = JsonPath.<List<Object>>read(eventsBetween("2026-11-01", "2026-11-30"), "$.events").size();
        int second = JsonPath.<List<Object>>read(eventsBetween("2026-11-01", "2026-11-30"), "$.events").size();
        int third = JsonPath.<List<Object>>read(eventsBetween("2026-11-01", "2026-11-30"), "$.events").size();

        assertThat(first).isEqualTo(second).isEqualTo(third);
    }

    @Test
    void updatingRuleRewritesFutureEventsOnly() throws Exception {
        // 9월(이미 지난 달)과 11월(앞으로 올 달)을 먼저 펼쳐 기록을 만들어 둔다
        eventsBetween("2026-09-01", "2026-09-30");
        eventsBetween("2026-11-01", "2026-11-30");

        mockMvc.perform(patch("/api/v1/recurring-rules/{id}", seedRuleId())
                        .contentType("application/json")
                        .content("{\"amount\":99000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(99000));

        // 미래분은 새 금액으로 다시 만들어진다
        String november = eventsBetween("2026-11-01", "2026-11-30");
        assertThat(JsonPath.<List<Integer>>read(november, "$.events[?(@.title=='통신비')].amount")).containsExactly(99000);

        // 이미 지난 9/10 분은 그대로 둔다
        String september = eventsBetween("2026-09-01", "2026-09-30");
        assertThat(JsonPath.<List<Integer>>read(september, "$.events[?(@.title=='통신비')].amount")).containsExactly(80000);
    }

    @Test
    void deactivatedRuleStopsGeneratingEvents() throws Exception {
        eventsBetween("2026-11-01", "2026-11-30");

        mockMvc.perform(delete("/api/v1/recurring-rules/{id}", seedRuleId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        assertThat(eventsTitled(eventsBetween("2026-11-01", "2026-11-30"), "통신비")).isEmpty();
    }

    private String seedRuleId() throws Exception {
        String body = mockMvc.perform(get("/api/v1/recurring-rules"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return String.valueOf(JsonPath.<Object>read(body, "$.recurringRules[0].recurringRuleId"));
    }

    // --- 카드 결제일 ------------------------------------------------------

    @Test
    void cardBillIsGeneratedFromPreviousMonthTransactions() throws Exception {
        // 시드의 8월 신한카드 사용분 240,000 + 180,000 + 100,000 = 520,000
        String september = eventsBetween("2026-09-01", "2026-09-30");
        assertThat(JsonPath.<List<Integer>>read(september, "$.events[?(@.title=='Deep Dream 결제')].amount"))
                .containsExactly(520000);
        assertThat(JsonPath.<List<String>>read(september, "$.events[?(@.title=='Deep Dream 결제')].eventDate"))
                .containsExactly("2026-09-05");
    }

    @Test
    void cardWithNoSpendingHasNoBill() throws Exception {
        // 현대카드(Zero)는 8월 사용분이 없으므로 청구 이벤트를 만들지 않는다
        assertThat(eventsTitled(eventsBetween("2026-09-01", "2026-09-30"), "Zero 결제")).isEmpty();
    }

    @Test
    void cardBillFollowsLaterAddedTransactions() throws Exception {
        eventsBetween("2026-10-01", "2026-10-31");   // 10월분 먼저 조회 (9월 카드 사용분 없음)

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType("application/json")
                        .content("""
                                {
                                  "transactionDate": "2026-09-20",
                                  "merchant": "백화점",
                                  "amount": 300000,
                                  "transactionType": "EXPENSE",
                                  "cardId": %s
                                }
                                """.formatted(shinhanCardId())))
                .andExpect(status().isOk());

        // 10/5 청구액이 나중에 추가된 9월 사용분을 따라간다
        String october = eventsBetween("2026-10-01", "2026-10-31");
        assertThat(JsonPath.<List<Integer>>read(october, "$.events[?(@.title=='Deep Dream 결제')].amount"))
                .containsExactly(300000);
    }

    private String shinhanCardId() throws Exception {
        String body = mockMvc.perform(get("/api/v1/cards"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return String.valueOf(JsonPath.<Object>read(body, "$.cards[0].cardId"));
    }

    // --- 거래 CRUD --------------------------------------------------------

    @Test
    void transactionCanBeCreatedUpdatedAndDeleted() throws Exception {
        String created = mockMvc.perform(post("/api/v1/transactions")
                        .contentType("application/json")
                        .content("""
                                {
                                  "transactionDate": "2026-09-27",
                                  "merchant": "편의점",
                                  "amount": 12000,
                                  "transactionType": "EXPENSE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchant").value("편의점"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String id = String.valueOf(JsonPath.<Object>read(created, "$.transactionId"));

        mockMvc.perform(patch("/api/v1/transactions/{id}", id)
                        .contentType("application/json")
                        .content("{\"amount\":15000,\"merchant\":\"편의점(수정)\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(15000))
                .andExpect(jsonPath("$.merchant").value("편의점(수정)"));

        mockMvc.perform(get("/api/v1/transactions").param("month", "2026-09").param("keyword", "편의점"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(delete("/api/v1/transactions/{id}", id))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/transactions").param("month", "2026-09").param("keyword", "편의점"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void transactionRejectsUnknownCategory() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType("application/json")
                        .content("""
                                {
                                  "transactionDate": "2026-09-27",
                                  "merchant": "테스트",
                                  "amount": 1000,
                                  "transactionType": "EXPENSE",
                                  "categoryId": 999999
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    // --- 대시보드 / 계좌 --------------------------------------------------

    @Test
    void dashboardExcludesEventsOutsidePeriod() throws Exception {
        String before = mockMvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        int availableBefore = JsonPath.read(before, "$.availableAmount");

        // 한참 뒤의 고정 지출은 이번 달 사용 가능 자금에 섞이면 안 된다
        mockMvc.perform(post("/api/v1/financial-events")
                        .contentType("application/json")
                        .content("""
                                {
                                  "eventDate": "2030-01-15",
                                  "title": "먼 미래 지출",
                                  "amount": 900000,
                                  "direction": "OUTFLOW",
                                  "eventType": "ETC",
                                  "fixed": true
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableAmount").value(availableBefore));
    }

    @Test
    void accountsTotalMatchesDashboardBalance() throws Exception {
        // 여행 적금(자산 미포함)은 양쪽 모두에서 빠져야 한다
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBalance").value(1200000))
                .andExpect(jsonPath("$.allAccountsBalance").value(2200000));

        mockMvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentBalance").value(1200000));
    }

    // --- 예산 -------------------------------------------------------------

    @Test
    void budgetCanBeQueriedByMonth() throws Exception {
        mockMvc.perform(get("/api/v1/budgets/monthly").param("month", "2026-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-09"))
                .andExpect(jsonPath("$.totalLimit").value(530000));
    }

    @Test
    void budgetStatusFlagsExceededCategories() throws Exception {
        // 시드 한도: 식비/카페 350,000. 9월 지출은 스타벅스 6,500 뿐이라 아직 여유가 있다.
        String before = mockMvc.perform(get("/api/v1/budgets/monthly/status").param("month", "2026-09"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(JsonPath.<List<Boolean>>read(before, "$.categories[?(@.category=='식비/카페')].exceeded"))
                .containsExactly(false);

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType("application/json")
                        .content("""
                                {
                                  "transactionDate": "2026-09-26",
                                  "merchant": "호텔 레스토랑",
                                  "amount": 600000,
                                  "transactionType": "EXPENSE",
                                  "categoryId": %s
                                }
                                """.formatted(foodCategoryId())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/budgets/monthly/status").param("month", "2026-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[?(@.category=='식비/카페')].exceeded").value(true))
                // 스타벅스 6,500 + 호텔 레스토랑 600,000 = 606,500 (한도 350,000 초과)
                .andExpect(jsonPath("$.categories[?(@.category=='식비/카페')].spent").value(606500))
                // 9월 총 지출 608,000 > 총 한도 530,000
                .andExpect(jsonPath("$.totalExceeded").value(true));
    }

    private String foodCategoryId() throws Exception {
        String body = mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Object> ids = JsonPath.read(body, "$.categories[?(@.name=='식비/카페')].categoryId");
        return String.valueOf(ids.getFirst());
    }
}
