package com.moneyweather.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API 키 없이 동작하는 규칙 기반 AI. 질문마다 다른 도구를 호출하고 실제 수치로 답해야 한다.
 */
class AgentRuleBasedTest extends ApiTestSupport {

    private String chat(Long conversationId, String message) throws Exception {
        String conv = conversationId == null ? "null" : conversationId.toString();
        return body(postJson("/api/v1/agent/chat", """
                {"conversationId":%s,"message":"%s"}
                """.formatted(conv, message)).andExpect(status().isOk()));
    }

    private List<String> sources(String chatResponse) {
        return read(chatResponse, "$.sources");
    }

    @Test
    void differentQuestionsCallDifferentTools() throws Exception {
        assertThat(sources(chat(null, "예산 얼마 남았어?"))).containsExactly("get_budget_status");
        assertThat(sources(chat(null, "25만원 쓰면 어떻게 돼?"))).containsExactly("simulate_spending");
        assertThat(sources(chat(null, "구독료 정리해줘"))).containsExactly("detect_recurring");
        assertThat(sources(chat(null, "어디에 돈을 많이 썼어?"))).containsExactly("analyze_spending");
        assertThat(sources(chat(null, "이번 달 얼마 써도 돼?"))).containsExactly("get_dashboard", "get_forecast");
        assertThat(sources(chat(null, "이번 달 위험해?"))).containsExactly("get_dashboard", "get_forecast", "get_alerts");
    }

    @Test
    void answersUseRealNumbers() throws Exception {
        String answer = read(chat(null, "이번 달 얼마 써도 돼?"), "$.answer");
        assertThat(answer).contains("561,000원").contains("흐림");
        assertThat((String) read(chat(null, "이번 달 얼마 써도 돼?"), "$.provider")).isEqualTo("rule-based");
    }

    @Test
    void simulationQuestionParsesAmountAndDate() throws Exception {
        String response = chat(null, "9월 12일에 25만원 쓰면 괜찮을까?");
        assertThat(readLong(response, "$.toolCalls[0].arguments.amount")).isEqualTo(250_000);
        assertThat((String) read(response, "$.toolCalls[0].arguments.spendingDate")).isEqualTo("2026-09-12");
        assertThat((String) read(response, "$.answer")).contains("311,000원");
    }

    @Test
    void budgetQuestionAboutAnotherMonthLooksUpThatMonth() throws Exception {
        // 예전에는 질문의 달을 읽지 않고 기준 월(2026-09) 예산으로 답했다
        String response = chat(null, "2027년 3월 예산 상황 알려줘");
        assertThat((String) read(response, "$.toolCalls[0].arguments.month")).isEqualTo("2027-03");
        assertThat((String) read(response, "$.answer")).contains("2027-03에는 설정된 예산이 없습니다");
    }

    @Test
    void lastMonthIsUnderstood() throws Exception {
        String response = chat(null, "지난달 어디에 돈을 많이 썼어?");
        assertThat((String) read(response, "$.toolCalls[0].arguments.month")).isEqualTo("2026-08");
        // 8월: 카드 520,000 + 넷플릭스 39,000 + 유튜브 14,900 + 통신비 80,000
        assertThat((String) read(response, "$.answer")).contains("653,900원");
    }

    @Test
    void recurringAnswerNamesUnregisteredCandidates() throws Exception {
        String answer = read(chat(null, "반복 지출 중 줄일 만한 게 있어?"), "$.answer");
        assertThat(answer).contains("넷플릭스").contains("유튜브 프리미엄").doesNotContain("통신비");
    }

    @Test
    void conversationKeepsHistory() throws Exception {
        long conversationId = readLong(chat(null, "이번 달 얼마 써도 돼?"), "$.conversationId");
        chat(conversationId, "예산은?");

        List<Object> messages = read(getOk("/api/v1/agent/conversations/" + conversationId + "/messages"), "$.messages");
        assertThat(messages).hasSize(4);
    }

    @Test
    void evidenceReturnsWhatTheAnswerWasBasedOnNotTodaysNumbers() throws Exception {
        long messageId = readLong(chat(null, "이번 달 얼마 써도 돼?"), "$.messageId");

        // 답변 뒤에 잔액이 바뀌어도
        patchJson("/api/v1/accounts/" + accountId("생활비 통장"), "{\"balance\":100000}").andExpect(status().isOk());
        assertThat(readLong(getOk("/api/v1/dashboard"), "$.availableAmount")).isEqualTo(-139_000);

        // 근거는 답변 당시 값 그대로다
        String evidence = getOk("/api/v1/agent/evidence/" + messageId);
        assertThat((String) read(evidence, "$.provider")).isEqualTo("rule-based");
        assertThat(((Number) first(evidence, "$.toolCalls[?(@.name=='get_dashboard')].result.availableAmount")).longValue()).isEqualTo(561_000);
    }

    @Test
    void blankMessageIsRejected() throws Exception {
        postJson("/api/v1/agent/chat", "{\"message\":\"  \"}").andExpect(status().isBadRequest());
    }

    @Test
    void suggestionsReflectCurrentSituation() throws Exception {
        // 자산 합계 700,000 → 9/15 잔액 61,000
        patchJson("/api/v1/accounts/" + accountId("생활비 통장"), "{\"balance\":300000}").andExpect(status().isOk());

        List<String> suggestions = read(getOk("/api/v1/agent/suggestions"), "$.suggestions");
        assertThat(suggestions).hasSize(4);
        assertThat(suggestions.getFirst()).contains("9월 15일").contains("61,000원");
        assertThat(suggestions).contains("넷플릭스를 반복 지출로 등록할까?");
    }

    @Test
    void recommendedActionsCarryRealNumbers() throws Exception {
        postJson("/api/v1/transactions", """
                {"transactionDate":"2026-09-20","merchant":"회식","amount":400000,"transactionType":"EXPENSE","categoryId":%d}
                """.formatted(categoryId("식비/카페"))).andExpect(status().isOk());

        String result = body(postJson("/api/v1/agent/recommend-actions", "").andExpect(status().isOk()));
        assertThat((String) first(result, "$.actions[?(@.type=='BUDGET_EXCEEDED')].message")).contains("식비/카페").contains("56,500원");
        assertThat((String) first(result, "$.actions[?(@.type=='REGISTER_RECURRING')].message")).contains("월");
        // 급한 것(우선순위 1)이 앞에 온다
        List<Integer> priorities = read(result, "$.actions[*].priority");
        assertThat(priorities).isSorted();
    }

    @Test
    void spendingCanBeAnalyzedForAnyMonth() throws Exception {
        // 8월: 카드 520,000 + 넷플릭스 39,000 + 유튜브 14,900 + 통신비 80,000
        String august = body(postJson("/api/v1/agent/analyze-spending?month=2026-08", "").andExpect(status().isOk()));
        assertThat(readLong(august, "$.totalExpense")).isEqualTo(653_900);
        assertThat((String) read(august, "$.ranking[0].category")).isEqualTo("식비/카페");
    }

    @Test
    void detectedCandidateCanBeRegisteredAsIs() throws Exception {
        String detected = body(postJson("/api/v1/agent/detect-recurring", "").andExpect(status().isOk()));
        assertThat(((Number) first(detected, "$.candidates[?(@.merchant=='넷플릭스')].confidence")).doubleValue()).isGreaterThan(0.7);

        // suggestedRule 을 그대로 반복 규칙으로 등록하면 후보에서 빠진다
        Object suggested = first(detected, "$.candidates[?(@.merchant=='넷플릭스')].suggestedRule");
        postJson("/api/v1/recurring-rules", new ObjectMapper().writeValueAsString(suggested)).andExpect(status().isOk());

        String after = body(postJson("/api/v1/agent/detect-recurring", "").andExpect(status().isOk()));
        List<Object> netflix = read(after, "$.candidates[?(@.merchant=='넷플릭스')]");
        assertThat(netflix).isEmpty();
    }
}
