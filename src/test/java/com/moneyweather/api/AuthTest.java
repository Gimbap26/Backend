package com.moneyweather.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneyweather.security.AuthTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 회원가입·로그인과 접근 제어. 토큰을 기본으로 붙이지 않는 MockMvc({@link #anonymous})로 검증한다.
 */
class AuthTest extends ApiTestSupport {
    private static final String DEMO_LOGIN = "{\"email\":\"demo@moneyweather.dev\",\"password\":\"demo1234!\"}";

    @Autowired
    WebApplicationContext context;
    @Autowired
    AuthTokenService tokens;
    @Autowired
    ObjectMapper objectMapper;

    private MockMvc anonymous;

    @BeforeEach
    void buildAnonymousClient() {
        anonymous = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private ResultActions anonymousPost(String url, String json) throws Exception {
        return anonymous.perform(post(url).contentType("application/json").content(json));
    }

    private String text(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String signup(String email) throws Exception {
        return text(anonymousPost("/api/v1/auth/signup", """
                {"email":"%s","password":"password123","name":"김밥"}
                """.formatted(email)).andExpect(status().isOk()));
    }

    private String tokenOf(String authResponse) {
        return read(authResponse, "$.accessToken");
    }

    // --- 가입과 로그인 ---------------------------------------------------------

    @Test
    void signupGivesATokenThatWorksRightAway() throws Exception {
        String token = tokenOf(signup("Kimbap@Example.com"));

        anonymous.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("kimbap@example.com"))
                .andExpect(jsonPath("$.name").value("김밥"));

        // 새 사용자는 기본 카테고리를 가지고 시작하고, 다른 사람의 계좌는 보이지 않는다
        anonymous.perform(get("/api/v1/categories").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.categories.length()").value(4));
        anonymous.perform(get("/api/v1/accounts").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.accounts.length()").value(0))
                .andExpect(jsonPath("$.totalBalance").value(0));
    }

    @Test
    void newUsersGetIdsThatDoNotCollideWithTheDemoUser() throws Exception {
        long id = readLong(signup("a@example.com"), "$.userId");
        assertThat(id).isGreaterThanOrEqualTo(1000);
    }

    @Test
    void demoAccountCanLogIn() throws Exception {
        String token = tokenOf(text(anonymousPost("/api/v1/auth/login", DEMO_LOGIN).andExpect(status().isOk())));
        anonymous.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.userId").value(1));
    }

    @Test
    void wrongPasswordAndUnknownEmailLookTheSame() throws Exception {
        String wrongPassword = text(anonymousPost("/api/v1/auth/login", "{\"email\":\"demo@moneyweather.dev\",\"password\":\"nope-nope\"}")
                .andExpect(status().isUnauthorized()));
        String unknownEmail = text(anonymousPost("/api/v1/auth/login", "{\"email\":\"nobody@example.com\",\"password\":\"nope-nope\"}")
                .andExpect(status().isUnauthorized()));
        assertThat((String) read(wrongPassword, "$.message")).isEqualTo(read(unknownEmail, "$.message"));
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        signup("dup@example.com");
        anonymousPost("/api/v1/auth/signup", "{\"email\":\"DUP@example.com\",\"password\":\"password123\",\"name\":\"B\"}")
                .andExpect(status().isConflict());
    }

    @Test
    void weakPasswordAndBadEmailAreRejected() throws Exception {
        anonymousPost("/api/v1/auth/signup", "{\"email\":\"x@example.com\",\"password\":\"short\",\"name\":\"A\"}").andExpect(status().isBadRequest());
        anonymousPost("/api/v1/auth/signup", "{\"email\":\"not-an-email\",\"password\":\"password123\",\"name\":\"A\"}").andExpect(status().isBadRequest());
    }

    @Test
    void passwordIsNeverStoredOrReturnedInPlainText() throws Exception {
        String response = signup("hash@example.com");
        assertThat(response).doesNotContain("password123");
    }

    // --- 토큰 검증 ------------------------------------------------------------

    @Test
    void requestsWithoutTokenAreRejectedWithJsonError() throws Exception {
        anonymous.perform(get("/api/v1/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/v1/dashboard"));
    }

    @Test
    void userIdHeaderAndQueryNoLongerAuthenticate() throws Exception {
        anonymous.perform(get("/api/v1/dashboard").header("X-User-Id", "1")).andExpect(status().isUnauthorized());
        anonymous.perform(get("/api/v1/dashboard").param("userId", "1")).andExpect(status().isUnauthorized());
    }

    @Test
    void forgedTokenIsRejected() throws Exception {
        String token = tokens.issue(1L, 0);
        String forged = token.substring(0, token.lastIndexOf('.') + 1) + "forgedsignature";
        anonymous.perform(get("/api/v1/dashboard").header("Authorization", "Bearer " + forged)).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String expired = new AuthTokenService(objectMapper, "dev-secret-change-me", -60).issue(1L, 0);
        anonymous.perform(get("/api/v1/dashboard").header("Authorization", "Bearer " + expired)).andExpect(status().isUnauthorized());
    }

    @Test
    void publicEndpointsStayOpen() throws Exception {
        anonymous.perform(get("/actuator/health")).andExpect(status().isOk());
        anonymous.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    // --- 다른 사용자의 데이터 ---------------------------------------------------

    @Test
    void userIdQueryCannotSwitchToAnotherUser() throws Exception {
        String tokenB = tokenOf(signup("b@example.com"));
        anonymous.perform(get("/api/v1/users/me").param("userId", "1").header("Authorization", "Bearer " + tokenB))
                .andExpect(jsonPath("$.email").value("b@example.com"));
    }

    @Test
    void otherUsersResourcesAreForbidden() throws Exception {
        long demoAccount = accountId("생활비 통장");
        long demoConversation = readLong(body(postJson("/api/v1/agent/conversations", "{\"title\":\"데모 대화\"}")), "$.conversationId");
        String tokenB = "Bearer " + tokenOf(signup("intruder@example.com"));

        anonymous.perform(patch("/api/v1/accounts/" + demoAccount).header("Authorization", tokenB)
                        .contentType("application/json").content("{\"balance\":0}"))
                .andExpect(status().isForbidden());
        anonymous.perform(get("/api/v1/agent/conversations/" + demoConversation + "/messages").header("Authorization", tokenB))
                .andExpect(status().isForbidden());
        anonymous.perform(post("/api/v1/transactions").header("Authorization", tokenB).contentType("application/json")
                        .content("{\"transactionDate\":\"2026-09-20\",\"merchant\":\"x\",\"amount\":1000,\"transactionType\":\"EXPENSE\",\"accountId\":" + demoAccount + "}"))
                .andExpect(status().isForbidden());

        // 데모 사용자의 잔액은 그대로다
        assertThat(balanceOf("생활비 통장")).isEqualTo(800_000);
        List<Object> intruderAccounts = read(text(anonymous.perform(get("/api/v1/accounts").header("Authorization", tokenB))), "$.accounts");
        assertThat(intruderAccounts).isEmpty();
    }
}
