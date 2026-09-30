package com.moneyweather.api;

import com.moneyweather.security.AuthTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(DemoUserTokenConfig.class)
class MoneyWeatherControllerTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthTokenService tokens;

    @Test
    void dashboardReturnsMoneyWeatherSummary() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentBalance").value(1200000))
                .andExpect(jsonPath("$.weather").value("CLOUDY"));
    }

    @Test
    void financialEventsCanBeQueriedByDateRange() throws Exception {
        mockMvc.perform(get("/api/v1/financial-events")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events.length()").value(4));
    }

    @Test
    void simulationReturnsBeforeAndAfter() throws Exception {
        mockMvc.perform(post("/api/v1/available-funds/simulations")
                        .contentType("application/json")
                        .content("""
                                {
                                  "spendingDate": "2026-09-12",
                                  "amount": 100000,
                                  "targetDate": "2026-09-30",
                                  "title": "추가 지출"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.difference").value(-100000));
    }

    @Test
    void monthlyBudgetCanBeSavedAndReadBack() throws Exception {
        mockMvc.perform(put("/api/v1/budgets/monthly")
                        .contentType("application/json")
                        .content("""
                                {
                                  "month": "2026-10",
                                  "categoryLimits": {
                                    "식비/카페": 300000,
                                    "교통": 90000
                                  },
                                  "totalLimit": 390000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-10"))
                .andExpect(jsonPath("$.totalLimit").value(390000));

        mockMvc.perform(get("/api/v1/budgets/monthly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryLimits['교통']").value(90000));
    }

    @Test
    void recurringRuleCreatesStoredRule() throws Exception {
        mockMvc.perform(post("/api/v1/recurring-rules")
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "보험료",
                                  "recurrenceType": "MONTHLY",
                                  "dayOfMonth": 20,
                                  "startDate": "2026-09-01",
                                  "amount": 120000,
                                  "eventType": "ETC",
                                  "direction": "OUTFLOW"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("보험료"));

        mockMvc.perform(get("/api/v1/recurring-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recurringRules.length()").value(2));
    }

    @Test
    void agentChatPersistsConversationMessages() throws Exception {
        String conversation = mockMvc.perform(post("/api/v1/agent/conversations")
                        .contentType("application/json")
                        .content("{\"title\":\"이번 달 분석\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String conversationId = conversation.replaceAll(".*\\\"conversationId\\\":(\\d+).*", "$1");

        mockMvc.perform(post("/api/v1/agent/chat")
                        .contentType("application/json")
                        .content("""
                                {
                                  "conversationId": %s,
                                  "message": "이번 달 위험해?"
                                }
                                """.formatted(conversationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources.length()").value(3));

        mockMvc.perform(get("/api/v1/agent/conversations/{id}/messages", conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(2));
    }

    @Test
    void agentSupportEndpointsReturnToolReadyData() throws Exception {
        mockMvc.perform(get("/api/v1/agent/suggestions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions.length()").value(4));

        mockMvc.perform(post("/api/v1/agent/analyze-spending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExpense").value(8000));

        // 6~8월 거래에서 넷플릭스·유튜브 프리미엄을 찾고, 이미 규칙으로 등록된 통신비는 제외한다
        mockMvc.perform(post("/api/v1/agent/detect-recurring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates.length()").value(2))
                .andExpect(jsonPath("$.candidates[?(@.merchant=='통신비')]").isEmpty());

        mockMvc.perform(post("/api/v1/agent/recommend-actions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actions.length()").isNotEmpty());
    }

    @Test
    void unknownCurrentUserCannotAccessResources() throws Exception {
        // 서명은 올바르지만 존재하지 않는 사용자의 토큰
        mockMvc.perform(get("/api/v1/dashboard").header("Authorization", "Bearer " + tokens.issue(999L, 0)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bearerTokenCanResolveCurrentUser() throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"demo@moneyweather.dev\",\"password\":\"demo1234!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = response.replaceAll(".*\\\"accessToken\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1));
    }
}
