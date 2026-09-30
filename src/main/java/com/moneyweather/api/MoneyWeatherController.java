package com.moneyweather.api;

import com.moneyweather.service.AlertService;
import com.moneyweather.service.AuthService;
import com.moneyweather.service.AuthService.User;
import com.moneyweather.service.DemoDataService;
import com.moneyweather.service.MoneyWeatherService;
import com.moneyweather.service.MoneyWeatherService.SimulationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

/** 홈 화면: 사용자, 대시보드, 예측, 시뮬레이션, 알림. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Money Weather", description = "대시보드, 잔액 예측, 지출 시뮬레이션, 위험 알림")
public class MoneyWeatherController {
    private final MoneyWeatherService service;
    private final AlertService alertService;
    private final AuthService authService;
    private final DemoDataService demoData;

    public MoneyWeatherController(MoneyWeatherService service, AlertService alertService, AuthService authService, DemoDataService demoData) {
        this.service = service;
        this.alertService = alertService;
        this.authService = authService;
        this.demoData = demoData;
    }

    @GetMapping("/users/me")
    @Operation(summary = "현재 사용자 조회", description = "Bearer 토큰으로 로그인한 사용자 정보를 반환합니다.")
    public User me() {
        return authService.me();
    }

    @PostMapping("/dev/seed")
    @Operation(summary = "개발용 시드 데이터 초기화", description = "모든 데이터를 지우고 데모 계정(demo@moneyweather.dev)과 계좌, 카드, 거래, 예정 이벤트, 반복 규칙, 월 예산을 다시 만듭니다. "
            + "운영 환경(postgres 프로필)에서는 꺼져 있어 404를 반환합니다.")
    public Map<String, Object> seed(@Valid @RequestBody SeedRequest request) {
        return demoData.seed(request.userId(), request.baseMonth(), request.reset());
    }

    @GetMapping("/forecasts")
    @Operation(summary = "잔액 예측 타임라인 조회", description = "기간 내 예정 이벤트를 반영한 일자별 예상 잔액, 최저 잔액일, 자금 날씨를 반환합니다.")
    public Map<String, Object> forecast(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.forecast(from, to);
    }

    @GetMapping("/dashboard")
    @Operation(summary = "홈 대시보드 조회", description = "현재 잔액, 고정 지출, 사용 가능 자금, 자금 날씨, 위험 요약, 다음 이벤트를 반환합니다.")
    public Map<String, Object> dashboard(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate) {
        return service.dashboard(baseDate, targetDate);
    }

    @PostMapping("/available-funds/simulations")
    @Operation(summary = "추가 지출 시뮬레이션", description = "추가 소비를 가정했을 때 사용 가능 자금과 자금 날씨 변화를 계산합니다.")
    public Map<String, Object> simulate(@Valid @RequestBody SimulationCreateRequest request) {
        return service.simulate(new SimulationRequest(request.spendingDate(), request.amount(), request.targetDate(), request.title()));
    }

    @GetMapping("/weather-statuses")
    @Operation(summary = "자금 날씨 상태 안내", description = "SUNNY, CLOUDY, RAINY, STORM 상태별 의미와 행동 가이드를 반환합니다.")
    public Map<String, Object> weatherStatuses() {
        return service.weatherStatuses();
    }

    @GetMapping("/forecast-alerts")
    @Operation(summary = "예측 위험 알림 조회", description = "잔액 마이너스·잔액 낮음 예상, 일주일 안의 결제, 예산 초과·임박을 심각도 순으로 반환합니다. 기간을 생략하면 기준 월 전체를 봅니다.")
    public Map<String, Object> forecastAlerts(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return alertService.alerts(from, to);
    }

    public record SeedRequest(@NotNull Long userId, @NotNull @DateTimeFormat(pattern = "yyyy-MM") YearMonth baseMonth, boolean reset) {}
    public record SimulationCreateRequest(@NotNull LocalDate spendingDate, @Positive long amount, @NotNull LocalDate targetDate, String title) {}
}
