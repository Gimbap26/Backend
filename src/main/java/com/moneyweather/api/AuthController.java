package com.moneyweather.api;

import com.moneyweather.security.AuthTokenService;
import com.moneyweather.service.MoneyWeatherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "개발 및 운영 인증 전환을 위한 토큰 API")
public class AuthController {
    private final AuthTokenService authTokenService;
    private final MoneyWeatherService service;

    public AuthController(AuthTokenService authTokenService, MoneyWeatherService service) {
        this.authTokenService = authTokenService;
        this.service = service;
    }

    @PostMapping("/dev-token")
    @Operation(summary = "개발용 Bearer 토큰 발급", description = "userId를 검증한 뒤 개발/테스트용 HMAC Bearer 토큰을 발급합니다.")
    public Map<String, Object> devToken(@Valid @RequestBody DevTokenRequest request) {
        service.getUser(request.userId());
        return Map.of("tokenType", "Bearer", "accessToken", authTokenService.issue(request.userId()));
    }

    public record DevTokenRequest(@NotNull Long userId) {}
}
