package com.moneyweather.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CORS, 로그아웃·비밀번호 변경에 따른 토큰 무효화, 로그인 실패 횟수 제한. */
class SecurityHardeningTest extends ApiTestSupport {
    @Autowired
    WebApplicationContext context;

    private MockMvc anonymous;

    @BeforeEach
    void buildAnonymousClient() {
        anonymous = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private ResultActions anonymousPost(String url, String json) throws Exception {
        return anonymous.perform(post(url).contentType("application/json").content(json));
    }

    private String signupToken(String email) throws Exception {
        String body = anonymousPost("/api/v1/auth/signup", """
                {"email":"%s","password":"password123","name":"김밥"}
                """.formatted(email)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return read(body, "$.accessToken");
    }

    private ResultActions login(String email, String password) throws Exception {
        return anonymousPost("/api/v1/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
    }

    private ResultActions me(String token) throws Exception {
        return anonymous.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token));
    }

    // --- CORS ---------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:5173", "http://localhost:5174", "http://localhost:3000"})
    void reactDevServersPassCorsPreflight(String origin) throws Exception {
        anonymous.perform(options("/api/v1/dashboard")
                        .header("Origin", origin)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", origin));
    }

    @Test
    void unknownOriginIsNotAllowed() throws Exception {
        anonymous.perform(options("/api/v1/dashboard")
                        .header("Origin", "http://evil.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void actualCrossOriginRequestCarriesCorsHeader() throws Exception {
        String token = signupToken("cors@example.com");
        anonymous.perform(get("/api/v1/users/me").header("Origin", "http://localhost:5173").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    // --- 로그아웃 · 비밀번호 변경 ------------------------------------------------

    @Test
    void logoutInvalidatesExistingTokens() throws Exception {
        String token = signupToken("logout@example.com");
        me(token).andExpect(status().isOk());

        anonymous.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token)).andExpect(status().isOk());

        me(token).andExpect(status().isUnauthorized());
        // 다시 로그인하면 새 토큰은 쓸 수 있다
        String fresh = read(login("logout@example.com", "password123").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.accessToken");
        me(fresh).andExpect(status().isOk());
    }

    @Test
    void logoutRequiresLogin() throws Exception {
        anonymous.perform(post("/api/v1/auth/logout")).andExpect(status().isUnauthorized());
    }

    @Test
    void changingPasswordKeepsThisDeviceAndLogsOutOthers() throws Exception {
        String otherDevice = signupToken("pw@example.com");
        String thisDevice = read(login("pw@example.com", "password123").andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.accessToken");

        String response = anonymous.perform(patch("/api/v1/auth/password").header("Authorization", "Bearer " + thisDevice)
                        .contentType("application/json").content("{\"currentPassword\":\"password123\",\"newPassword\":\"newpassword456\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String newToken = read(response, "$.accessToken");

        me(newToken).andExpect(status().isOk());
        me(thisDevice).andExpect(status().isUnauthorized());
        me(otherDevice).andExpect(status().isUnauthorized());
        login("pw@example.com", "password123").andExpect(status().isUnauthorized());
        login("pw@example.com", "newpassword456").andExpect(status().isOk());
    }

    @Test
    void wrongCurrentPasswordIsRejectedWithoutLoggingOut() throws Exception {
        String token = signupToken("pw2@example.com");
        anonymous.perform(patch("/api/v1/auth/password").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{\"currentPassword\":\"wrong-pass\",\"newPassword\":\"newpassword456\"}"))
                .andExpect(status().isBadRequest());
        me(token).andExpect(status().isOk());
    }

    // --- 로그인 실패 제한 --------------------------------------------------------

    @Test
    void repeatedFailuresForOneEmailAreBlockedEvenWithTheRightPassword() throws Exception {
        signupToken("target@example.com");
        for (int i = 0; i < 5; i++) login("target@example.com", "wrong-pass").andExpect(status().isUnauthorized());

        login("target@example.com", "password123").andExpect(status().isTooManyRequests());
        // 다른 계정은 영향을 받지 않는다
        login("demo@moneyweather.dev", "demo1234!").andExpect(status().isOk());
    }

    @Test
    void successfulLoginResetsTheEmailCounter() throws Exception {
        signupToken("reset@example.com");
        for (int i = 0; i < 4; i++) login("reset@example.com", "wrong-pass").andExpect(status().isUnauthorized());
        login("reset@example.com", "password123").andExpect(status().isOk());

        for (int i = 0; i < 4; i++) login("reset@example.com", "wrong-pass").andExpect(status().isUnauthorized());
        login("reset@example.com", "password123").andExpect(status().isOk());
    }

    @Test
    void manyFailuresFromOneIpAcrossAccountsAreBlocked() throws Exception {
        for (int i = 0; i < 20; i++) login("spray" + i + "@example.com", "wrong-pass").andExpect(status().isUnauthorized());
        login("demo@moneyweather.dev", "demo1234!").andExpect(status().isTooManyRequests());
    }
}
