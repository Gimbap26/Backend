package com.moneyweather.api;

import com.jayway.jsonpath.JsonPath;
import com.moneyweather.security.LoginAttemptService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API 통합 테스트 공통 기반.
 * H2 메모리 DB 를 모든 테스트 클래스가 공유하므로 각 테스트 전후로 시드를 초기화해
 * 실행 순서와 상관없이 서로 간섭하지 않게 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(DemoUserTokenConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class ApiTestSupport {
    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    private LoginAttemptService loginAttempts;

    @BeforeEach
    void reseed() throws Exception {
        // 테스트 요청은 모두 같은 IP 에서 오므로, 다른 테스트의 로그인 실패 기록이 섞이지 않게 비운다
        loginAttempts.clear();
        seed();
    }

    @AfterAll
    void restoreSeedForOtherTests() throws Exception {
        seed();
    }

    protected void seed() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed")
                        .contentType("application/json")
                        .content("{\"userId\":1,\"baseMonth\":\"2026-09\",\"reset\":true}"))
                .andExpect(status().isOk());
    }

    // --- 요청 ---------------------------------------------------------------

    protected String getOk(String url, String... params) throws Exception {
        var request = get(url);
        for (int i = 0; i + 1 < params.length; i += 2) request.param(params[i], params[i + 1]);
        return body(mockMvc.perform(request).andExpect(status().isOk()));
    }

    protected ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url).contentType("application/json").content(json));
    }

    protected ResultActions patchJson(String url, String json) throws Exception {
        return mockMvc.perform(patch(url).contentType("application/json").content(json));
    }

    protected ResultActions deleteReq(String url) throws Exception {
        return mockMvc.perform(delete(url));
    }

    protected String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    // --- 응답 읽기 ------------------------------------------------------------

    protected <T> T read(String json, String path) {
        return JsonPath.read(json, path);
    }

    protected long readLong(String json, String path) {
        return ((Number) JsonPath.read(json, path)).longValue();
    }

    /** 필터 경로({@code $.a[?(@.x=='y')].id})의 첫 번째 결과. */
    protected Object first(String json, String filterPath) {
        List<Object> found = JsonPath.read(json, filterPath);
        if (found.isEmpty()) throw new AssertionError("No match for " + filterPath + " in " + json);
        return found.getFirst();
    }

    // --- 시드 데이터 찾기 -----------------------------------------------------

    protected long accountId(String accountName) throws Exception {
        return ((Number) first(getOk("/api/v1/accounts"), "$.accounts[?(@.accountName=='" + accountName + "')].accountId")).longValue();
    }

    protected long balanceOf(String accountName) throws Exception {
        return ((Number) first(getOk("/api/v1/accounts"), "$.accounts[?(@.accountName=='" + accountName + "')].balance")).longValue();
    }

    protected long categoryId(String name) throws Exception {
        return ((Number) first(getOk("/api/v1/categories"), "$.categories[?(@.name=='" + name + "')].categoryId")).longValue();
    }

    protected long cardId(String cardName) throws Exception {
        return ((Number) first(getOk("/api/v1/cards"), "$.cards[?(@.cardName=='" + cardName + "')].cardId")).longValue();
    }

    protected long eventId(String from, String to, String title) throws Exception {
        return ((Number) first(getOk("/api/v1/financial-events", "from", from, "to", to),
                "$.events[?(@.title=='" + title + "')].eventId")).longValue();
    }
}
