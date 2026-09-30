package com.moneyweather.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI 에 Authorize 버튼을 달아, 로그인으로 받은 토큰을 넣고 API 를 바로 호출할 수 있게 한다. */
@Configuration
public class OpenApiConfig {
    private static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI moneyWeatherOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Money Weather API").version("v1")
                        .description("POST /api/v1/auth/login 으로 받은 accessToken 을 오른쪽 위 Authorize 에 넣으세요."))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
