package com.moneyweather.api;

import com.moneyweather.security.AuthTokenService;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 테스트의 MockMvc 요청에 데모 사용자(1번) 토큰을 기본으로 붙인다.
 * 요청에서 Authorization 헤더를 직접 지정하면 그 값이 우선한다.
 * 인증 자체를 검증하는 테스트는 토큰 없는 MockMvc 를 따로 만든다({@link AuthTest}).
 */
@TestConfiguration
class DemoUserTokenConfig {
    @Bean
    MockMvcBuilderCustomizer demoUserToken(AuthTokenService tokens) {
        return builder -> builder.defaultRequest(get("/").header("Authorization", "Bearer " + tokens.issue(1L, 0)));
    }
}
