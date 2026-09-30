package com.moneyweather.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 거래 검색이 DB 에서 페이지 단위로 동작하는지. */
class TransactionPagingTest extends ApiTestSupport {

    private void createTransactions(int count) throws Exception {
        for (int i = 1; i <= count; i++) {
            postJson("/api/v1/transactions", """
                    {"transactionDate":"2026-09-%02d","merchant":"페이징 테스트 %d","amount":%d,"transactionType":"EXPENSE"}
                    """.formatted(i, i, i * 1000)).andExpect(status().isOk());
        }
    }

    @Test
    void pagesAreServedNewestFirstWithTotalsForAllMatches() throws Exception {
        createTransactions(25);   // 9/1 ~ 9/25, 1,000 ~ 25,000원

        String first = getOk("/api/v1/transactions", "month", "2026-09", "keyword", "페이징", "size", "10");
        assertThat(readLong(first, "$.totalElements")).isEqualTo(25);
        assertThat(readLong(first, "$.totalPages")).isEqualTo(3);
        List<String> dates = read(first, "$.items[*].date");
        assertThat(dates).hasSize(10).startsWith("2026-09-25");
        // 합계는 현재 페이지가 아니라 조건에 맞는 25건 전체: 1,000 + ... + 25,000
        assertThat(readLong(first, "$.totalAmount")).isEqualTo(325_000);

        String last = getOk("/api/v1/transactions", "month", "2026-09", "keyword", "페이징", "size", "10", "page", "2");
        List<String> lastDates = read(last, "$.items[*].date");
        assertThat(lastDates).containsExactly("2026-09-05", "2026-09-04", "2026-09-03", "2026-09-02", "2026-09-01");
    }

    @Test
    void keywordIsCaseInsensitiveAndTreatsWildcardsLiterally() throws Exception {
        postJson("/api/v1/transactions", """
                {"transactionDate":"2026-09-10","merchant":"Coupang 100%% 할인","amount":5000,"transactionType":"EXPENSE"}
                """.formatted()).andExpect(status().isOk());

        assertThat(readLong(getOk("/api/v1/transactions", "month", "2026-09", "keyword", "coupang"), "$.totalElements")).isEqualTo(1);
        // '%' 가 모든 문자와 맞는 와일드카드로 해석되면 9월 거래 전부가 나온다
        assertThat(readLong(getOk("/api/v1/transactions", "month", "2026-09", "keyword", "%"), "$.totalElements")).isEqualTo(1);
    }

    @Test
    void oversizedPageIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/transactions")
                        .param("month", "2026-09").param("size", "101"))
                .andExpect(status().isBadRequest());
    }
}
