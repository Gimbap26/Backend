package com.moneyweather.api;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 거래·결제 완료 이벤트의 잔액 반영과 계좌·카드·카테고리 CRUD.
 * 시드 잔액: 생활비 통장 800,000 / 비상금 400,000 / 여행 적금 1,000,000(자산 미포함).
 */
class LedgerAndAssetTest extends ApiTestSupport {
    private static final String SEPT_FROM = "2026-09-01";
    private static final String SEPT_TO = "2026-09-30";

    private String tx(String type, long amount, String extra) {
        return """
                {"transactionDate":"2026-09-20","merchant":"테스트","amount":%d,"transactionType":"%s"%s}
                """.formatted(amount, type, extra.isEmpty() ? "" : "," + extra);
    }

    // --- 거래 → 잔액 ----------------------------------------------------------

    @Test
    void expenseFromAccountReducesBalance() throws Exception {
        long living = accountId("생활비 통장");
        postJson("/api/v1/transactions", tx("EXPENSE", 50_000, "\"accountId\":" + living)).andExpect(status().isOk());

        assertThat(balanceOf("생활비 통장")).isEqualTo(750_000);
        assertThat(readLong(getOk("/api/v1/dashboard"), "$.currentBalance")).isEqualTo(1_150_000);
    }

    @Test
    void incomeIncreasesBalance() throws Exception {
        long living = accountId("생활비 통장");
        postJson("/api/v1/transactions", tx("INCOME", 30_000, "\"accountId\":" + living)).andExpect(status().isOk());

        assertThat(balanceOf("생활비 통장")).isEqualTo(830_000);
    }

    @Test
    void transferMovesMoneyBetweenAccounts() throws Exception {
        long living = accountId("생활비 통장");
        long savings = accountId("여행 적금");
        postJson("/api/v1/transactions", tx("TRANSFER", 100_000, "\"accountId\":%d,\"transferAccountId\":%d".formatted(living, savings)))
                .andExpect(status().isOk());

        assertThat(balanceOf("생활비 통장")).isEqualTo(700_000);
        assertThat(balanceOf("여행 적금")).isEqualTo(1_100_000);
        // 적금은 자산에 포함되지 않으므로, 적금으로 옮긴 돈은 사용 가능한 돈에서 빠진다
        assertThat(readLong(getOk("/api/v1/accounts"), "$.totalBalance")).isEqualTo(1_100_000);
    }

    @Test
    void updatingTransactionReappliesItsEffect() throws Exception {
        long living = accountId("생활비 통장");
        String created = body(postJson("/api/v1/transactions", tx("EXPENSE", 50_000, "\"accountId\":" + living)).andExpect(status().isOk()));
        long id = readLong(created, "$.transactionId");

        patchJson("/api/v1/transactions/" + id, "{\"amount\":80000}").andExpect(status().isOk());
        assertThat(balanceOf("생활비 통장")).isEqualTo(720_000);

        // 카드 결제로 바꾸면 계좌에서 바로 빠지지 않으므로 잔액이 원래대로 돌아온다
        patchJson("/api/v1/transactions/" + id, "{\"cardId\":" + cardId("Deep Dream") + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").doesNotExist());
        assertThat(balanceOf("생활비 통장")).isEqualTo(800_000);
    }

    @Test
    void invalidTransactionPatchIsRejected() throws Exception {
        long living = accountId("생활비 통장");
        String created = body(postJson("/api/v1/transactions", tx("EXPENSE", 50_000, "\"accountId\":" + living)).andExpect(status().isOk()));
        long id = readLong(created, "$.transactionId");

        patchJson("/api/v1/transactions/" + id, "{\"amount\":-1}").andExpect(status().isBadRequest());
        patchJson("/api/v1/transactions/" + id, "{\"merchant\":\"\"}").andExpect(status().isBadRequest());
        assertThat(balanceOf("생활비 통장")).isEqualTo(750_000);
    }

    @Test
    void deletingTransactionRestoresBalance() throws Exception {
        long living = accountId("생활비 통장");
        long id = readLong(body(postJson("/api/v1/transactions", tx("EXPENSE", 50_000, "\"accountId\":" + living))), "$.transactionId");

        deleteReq("/api/v1/transactions/" + id).andExpect(status().isOk());
        assertThat(balanceOf("생활비 통장")).isEqualTo(800_000);
    }

    @Test
    void invalidPaymentSourcesAreRejected() throws Exception {
        long living = accountId("생활비 통장");
        // 계좌와 카드를 동시에 지정
        postJson("/api/v1/transactions", tx("EXPENSE", 1_000, "\"accountId\":%d,\"cardId\":%d".formatted(living, cardId("Deep Dream"))))
                .andExpect(status().isBadRequest());
        // 받는 계좌 없는 이체
        postJson("/api/v1/transactions", tx("TRANSFER", 1_000, "\"accountId\":" + living)).andExpect(status().isBadRequest());
        // 같은 계좌로 이체
        postJson("/api/v1/transactions", tx("TRANSFER", 1_000, "\"accountId\":%d,\"transferAccountId\":%d".formatted(living, living)))
                .andExpect(status().isBadRequest());

        assertThat(balanceOf("생활비 통장")).isEqualTo(800_000);
    }

    // --- 이벤트 결제 완료 → 잔액 ------------------------------------------------

    @Test
    void cardSpendingHitsBalanceOnlyWhenBillIsPaid() throws Exception {
        postJson("/api/v1/transactions", tx("EXPENSE", 70_000, "\"cardId\":" + cardId("Deep Dream"))).andExpect(status().isOk());
        assertThat(balanceOf("생활비 통장")).isEqualTo(800_000);

        // 9/5 카드 청구(8월 사용분 520,000)를 결제 완료 처리하면 결제 계좌에서 빠진다
        long bill = eventId(SEPT_FROM, SEPT_TO, "Deep Dream 결제");
        patchJson("/api/v1/financial-events/" + bill, "{\"status\":\"PAID\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
        assertThat(balanceOf("생활비 통장")).isEqualTo(280_000);
    }

    @Test
    void paidEventMovesFromForecastIntoBalance() throws Exception {
        String before = getOk("/api/v1/dashboard");
        assertThat(readLong(before, "$.availableAmount")).isEqualTo(561_000);

        long netflix = eventId(SEPT_FROM, SEPT_TO, "넷플릭스");
        patchJson("/api/v1/financial-events/" + netflix, "{\"status\":\"PAID\"}").andExpect(status().isOk());

        // 넷플릭스 39,000 이 '앞으로 나갈 돈'에서 '이미 나간 돈'으로 옮겨질 뿐, 쓸 수 있는 돈은 같다
        String after = getOk("/api/v1/dashboard");
        assertThat(readLong(after, "$.currentBalance")).isEqualTo(1_161_000);
        assertThat(readLong(after, "$.fixedOutflows")).isEqualTo(600_000);
        assertThat(readLong(after, "$.availableAmount")).isEqualTo(561_000);
    }

    @Test
    void cancelingPaidEventRestoresBalance() throws Exception {
        long netflix = eventId(SEPT_FROM, SEPT_TO, "넷플릭스");
        patchJson("/api/v1/financial-events/" + netflix, "{\"status\":\"PAID\"}").andExpect(status().isOk());
        assertThat(balanceOf("생활비 통장")).isEqualTo(761_000);

        deleteReq("/api/v1/financial-events/" + netflix).andExpect(status().isOk());
        assertThat(balanceOf("생활비 통장")).isEqualTo(800_000);
    }

    @Test
    void changingAmountOfPaidEventAdjustsBalance() throws Exception {
        long netflix = eventId(SEPT_FROM, SEPT_TO, "넷플릭스");
        patchJson("/api/v1/financial-events/" + netflix, "{\"status\":\"PAID\"}").andExpect(status().isOk());
        patchJson("/api/v1/financial-events/" + netflix, "{\"amount\":45000}").andExpect(status().isOk());

        assertThat(balanceOf("생활비 통장")).isEqualTo(755_000);
    }

    @Test
    void invalidFinancialEventPatchIsRejected() throws Exception {
        long netflix = eventId(SEPT_FROM, SEPT_TO, "넷플릭스");

        patchJson("/api/v1/financial-events/" + netflix, "{\"amount\":-1}").andExpect(status().isBadRequest());
        patchJson("/api/v1/financial-events/" + netflix, "{\"title\":\"\"}").andExpect(status().isBadRequest());

        String events = getOk("/api/v1/financial-events", "from", SEPT_FROM, "to", SEPT_TO);
        assertThat(((Number) first(events, "$.events[?(@.title=='넷플릭스')].amount")).longValue()).isEqualTo(39_000);
    }

    @Test
    void payingEventWithoutAccountIsRejected() throws Exception {
        String created = body(postJson("/api/v1/financial-events", """
                {"eventDate":"2026-09-20","title":"계좌 없는 지출","amount":10000,"direction":"OUTFLOW","eventType":"ETC","fixed":true}
                """).andExpect(status().isOk()));
        long id = readLong(created, "$.eventId");

        patchJson("/api/v1/financial-events/" + id, "{\"status\":\"PAID\"}").andExpect(status().isBadRequest());
        // 결제 계좌를 함께 지정하면 처리된다
        patchJson("/api/v1/financial-events/" + id, "{\"status\":\"PAID\",\"accountId\":" + accountId("비상금") + "}").andExpect(status().isOk());
        assertThat(balanceOf("비상금")).isEqualTo(390_000);
    }

    // --- 계좌 CRUD ----------------------------------------------------------

    @Test
    void balanceCanBeOverriddenManually() throws Exception {
        patchJson("/api/v1/accounts/" + accountId("비상금"), "{\"balance\":123456}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(123456));
        assertThat(balanceOf("비상금")).isEqualTo(123_456);
    }

    @Test
    void accountInUseCannotBeDeleted() throws Exception {
        // 생활비 통장은 카드 결제 계좌, 반복 규칙, 예정 이벤트에서 쓰인다
        deleteReq("/api/v1/accounts/" + accountId("생활비 통장")).andExpect(status().isConflict());
    }

    @Test
    void accountCanBeCreatedAndDeleted() throws Exception {
        String created = body(postJson("/api/v1/accounts", """
                {"bankName":"토스뱅크","accountName":"파킹 통장","balance":50000}
                """).andExpect(status().isOk()));
        assertThat((Boolean) read(created, "$.includedInAssets")).isTrue();
        assertThat(readLong(getOk("/api/v1/accounts"), "$.totalBalance")).isEqualTo(1_250_000);

        deleteReq("/api/v1/accounts/" + readLong(created, "$.accountId")).andExpect(status().isOk());
        assertThat(readLong(getOk("/api/v1/accounts"), "$.totalBalance")).isEqualTo(1_200_000);
    }

    // --- 카드 CRUD ----------------------------------------------------------

    @Test
    void cardCanBeCreatedWithPaymentAccount() throws Exception {
        long emergency = accountId("비상금");
        postJson("/api/v1/cards", """
                {"cardCompany":"KB국민카드","cardName":"톡톡","paymentDay":25,"paymentAccountId":%d}
                """.formatted(emergency))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentAccountId").value(emergency))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void invalidPaymentDayIsRejected() throws Exception {
        postJson("/api/v1/cards", "{\"cardCompany\":\"A\",\"cardName\":\"B\",\"paymentDay\":40}").andExpect(status().isBadRequest());
    }

    @Test
    void deletingCardDeactivatesIt() throws Exception {
        long zero = cardId("Zero");
        deleteReq("/api/v1/cards/" + zero).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));

        List<Object> activeZero = read(getOk("/api/v1/cards", "active", "true"), "$.cards[?(@.cardName=='Zero')]");
        assertThat(activeZero).isEmpty();
    }

    // --- 카테고리 CRUD ------------------------------------------------------

    @Test
    void defaultCategoryCannotBeDeleted() throws Exception {
        deleteReq("/api/v1/categories/" + categoryId("식비/카페")).andExpect(status().isConflict());
    }

    @Test
    void duplicateCategoryNameIsRejected() throws Exception {
        postJson("/api/v1/categories", "{\"name\":\"교통\",\"categoryType\":\"EXPENSE\"}").andExpect(status().isConflict());
    }

    @Test
    void renamingCategoryRenamesItsBudgetLimit() throws Exception {
        patchJson("/api/v1/categories/" + categoryId("식비/카페"), "{\"name\":\"식비\"}").andExpect(status().isOk());

        Map<String, Integer> limits = read(getOk("/api/v1/budgets/monthly", "month", "2026-09"), "$.categoryLimits");
        assertThat(limits).containsEntry("식비", 350_000).doesNotContainKey("식비/카페");

        // 예산 대조도 끊기지 않는다: 스타벅스 6,500 이 새 이름으로 집계된다
        String status = getOk("/api/v1/budgets/monthly/status", "month", "2026-09");
        assertThat(((Number) first(status, "$.categories[?(@.category=='식비')].spent")).longValue()).isEqualTo(6_500);
    }

    @Test
    void deletingCategoryUncategorizesItsTransactions() throws Exception {
        long hobby = readLong(body(postJson("/api/v1/categories", "{\"name\":\"취미\",\"categoryType\":\"EXPENSE\"}")
                .andExpect(status().isOk())), "$.categoryId");
        postJson("/api/v1/transactions", tx("EXPENSE", 20_000, "\"categoryId\":" + hobby).replace("테스트", "보드게임카페"))
                .andExpect(status().isOk());

        deleteReq("/api/v1/categories/" + hobby)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uncategorizedTransactions").value(1));

        String txs = getOk("/api/v1/transactions", "month", "2026-09", "keyword", "보드게임");
        assertThat((String) first(txs, "$.items[*].category")).isEqualTo("미분류");
    }
}
