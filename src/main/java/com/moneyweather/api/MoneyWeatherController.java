package com.moneyweather.api;

import com.moneyweather.domain.Enums.*;
import com.moneyweather.service.MoneyWeatherService;
import com.moneyweather.service.MoneyWeatherService.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Money Weather", description = "자금 날씨 서비스의 계좌, 카드, 거래, 예정 이벤트, 반복 규칙, 예산, AI Agent API")
public class MoneyWeatherController {
    private final MoneyWeatherService service;

    public MoneyWeatherController(MoneyWeatherService service) {
        this.service = service;
    }

    @GetMapping("/users/me")
    @Operation(summary = "현재 사용자 조회", description = "Bearer 토큰, X-User-Id 헤더 또는 userId query로 해석된 현재 사용자 정보를 반환합니다.")
    public User me(@RequestParam(required = false) Long userId) {
        return service.getUser(userId);
    }

    @PostMapping("/dev/seed")
    @Operation(summary = "개발용 시드 데이터 생성", description = "데모 사용자, 계좌, 카드, 카테고리, 거래, 예정 이벤트, 반복 규칙, 월 예산을 초기화합니다.")
    public Map<String, Object> seed(@Valid @RequestBody SeedRequest request) {
        return service.seed(request.userId(), request.baseMonth(), request.reset());
    }

    @GetMapping("/accounts")
    @Operation(summary = "계좌 목록 및 잔액 조회", description = "자산 포함 여부 필터를 적용해 계좌 목록과 총 잔액을 반환합니다.")
    public Map<String, Object> accounts(@RequestParam(required = false) Long userId,
                                        @RequestParam(defaultValue = "false") boolean includedOnly) {
        service.getUser(userId);
        return service.accounts(includedOnly);
    }

    @GetMapping("/cards")
    @Operation(summary = "카드 목록 조회", description = "사용자의 카드사, 카드명, 결제일, 활성 상태를 반환합니다.")
    public Map<String, Object> cards(@RequestParam(required = false) Long userId,
                                     @RequestParam(required = false) Boolean active) {
        service.getUser(userId);
        return service.cards(active);
    }

    @GetMapping("/categories")
    @Operation(summary = "카테고리 목록 조회", description = "거래 분류에 사용하는 수입, 지출, 이체 카테고리를 반환합니다.")
    public Map<String, Object> categories(@RequestParam(required = false) Long userId,
                                          @RequestParam(required = false) CategoryType categoryType) {
        service.getUser(userId);
        return service.categories(categoryType);
    }

    @GetMapping("/transactions")
    @Operation(summary = "거래 내역 조회", description = "월, 거래 유형, 카테고리, 키워드, 페이지 조건으로 거래 내역을 조회합니다.")
    public Map<String, Object> transactions(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                                            @RequestParam(required = false) TransactionType transactionType,
                                            @RequestParam(required = false) Long categoryId,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(defaultValue = "0") @Min(0) int page,
                                            @RequestParam(defaultValue = "20") @Min(1) int size) {
        return service.transactions(month, transactionType, categoryId, keyword, page, size);
    }

    @PatchMapping("/transactions/{transactionId}/category")
    @Operation(summary = "거래 카테고리 변경", description = "거래의 카테고리를 변경합니다. 현재 사용자 소유 거래와 카테고리만 수정할 수 있습니다.")
    public Map<String, Object> updateTransactionCategory(@PathVariable long transactionId,
                                                         @Valid @RequestBody CategoryPatch request) {
        return service.updateTransactionCategory(transactionId, request.categoryId());
    }

    @GetMapping("/financial-events")
    @Operation(summary = "예정 금융 이벤트 조회", description = "조회 기간, 방향, 이벤트 유형, 상태 조건으로 예정 지출/입금 이벤트를 반환합니다.")
    public Map<String, Object> financialEvents(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                               @RequestParam(required = false) Direction direction,
                                               @RequestParam(required = false) EventType eventType,
                                               @RequestParam(required = false) EventStatus status) {
        return service.financialEvents(from, to, direction, eventType, status);
    }

    @PostMapping("/financial-events")
    @Operation(summary = "예정 금융 이벤트 등록", description = "카드 결제, 통신비, 구독, 급여 등 미래 현금흐름 이벤트를 등록합니다.")
    public FinancialEvent createEvent(@Valid @RequestBody EventCreateRequest request) {
        return service.createEvent(new EventMutation(request.eventDate(), request.title(), request.amount(), request.direction(), request.eventType(), request.fixed()));
    }

    @PatchMapping("/financial-events/{eventId}")
    @Operation(summary = "예정 금융 이벤트 수정", description = "이벤트 일자, 제목, 금액, 상태, 고정 지출 여부를 수정합니다.")
    public Map<String, Object> updateEvent(@PathVariable long eventId, @RequestBody EventPatch request) {
        return service.updateEvent(eventId, request);
    }

    @DeleteMapping("/financial-events/{eventId}")
    @Operation(summary = "예정 금융 이벤트 취소", description = "이벤트를 삭제하지 않고 CANCELED 상태로 변경합니다.")
    public Map<String, Object> deleteEvent(@PathVariable long eventId) {
        return service.cancelEvent(eventId);
    }

    @PostMapping("/recurring-rules")
    @Operation(summary = "반복 규칙 생성", description = "월 반복 지출/입금 규칙을 생성하고 해당 월 예정 이벤트를 함께 생성합니다.")
    public Map<String, Object> createRule(@Valid @RequestBody RecurringCreateRequest request) {
        RecurringRule rule = service.createRule(new RecurringMutation(request.title(), request.recurrenceType(), request.dayOfMonth(), request.startDate(), request.endDate(), request.amount(), request.eventType(), request.direction()));
        return Map.of("recurringRuleId", rule.recurringRuleId(), "title", rule.title(), "recurrenceType", rule.recurrenceType(), "generatedEvents", 1);
    }

    @GetMapping("/recurring-rules")
    @Operation(summary = "반복 규칙 목록 조회", description = "등록된 반복 지출/입금 규칙 목록을 반환합니다.")
    public Map<String, Object> rules() {
        return service.rules();
    }

    @PatchMapping("/recurring-rules/{id}")
    @Operation(summary = "반복 규칙 수정", description = "반복 규칙의 제목, 금액, 일자, 활성 상태 등을 수정합니다.")
    public RecurringRule updateRule(@PathVariable long id, @RequestBody RecurringPatch request) {
        return service.updateRule(id, request);
    }

    @DeleteMapping("/recurring-rules/{id}")
    @Operation(summary = "반복 규칙 비활성화", description = "반복 규칙을 삭제하지 않고 비활성 상태로 변경합니다.")
    public Map<String, Object> deleteRule(@PathVariable long id) {
        return service.deleteRule(id);
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

    @PostMapping("/agent/conversations")
    @Operation(summary = "AI 대화 생성", description = "AI Agent 대화 세션을 생성하고 conversationId를 반환합니다.")
    public Map<String, Object> startConversation(@RequestBody(required = false) ConversationCreateRequest request) {
        return service.startConversation(request == null ? null : request.title());
    }

    @PostMapping("/agent/chat")
    @Operation(summary = "AI Agent 질문", description = "대시보드, 예정 이벤트, 예측 도구 스냅샷을 근거로 AI 답변을 생성하고 메시지를 저장합니다.")
    public Map<String, Object> chat(@Valid @RequestBody ChatRequest request) {
        return service.chat(request);
    }

    @GetMapping("/agent/conversations/{id}/messages")
    @Operation(summary = "AI 대화 메시지 조회", description = "대화에 저장된 사용자/AI 메시지를 시간순으로 반환합니다.")
    public Map<String, Object> messages(@PathVariable long id) {
        return service.messages(id);
    }

    @GetMapping("/agent/suggestions")
    @Operation(summary = "AI 추천 질문 조회", description = "홈 화면이나 AI 화면에서 보여줄 추천 질문 칩을 반환합니다.")
    public Map<String, Object> agentSuggestions() {
        return service.agentSuggestions();
    }

    @PostMapping("/agent/analyze-spending")
    @Operation(summary = "소비 패턴 분석", description = "현재 기준 월의 지출 거래를 카테고리별로 집계합니다.")
    public Map<String, Object> analyzeSpending() {
        return service.analyzeSpending();
    }

    @PostMapping("/agent/detect-recurring")
    @Operation(summary = "반복 지출 후보 탐지", description = "현재 등록된 활성 반복 규칙을 AI 분석용 반복 지출 후보로 반환합니다.")
    public Map<String, Object> detectRecurring() {
        return service.detectRecurring();
    }

    @PostMapping("/agent/recommend-actions")
    @Operation(summary = "행동 추천", description = "현재 자금 날씨와 위험 요약을 기준으로 사용자가 취할 행동을 추천합니다.")
    public Map<String, Object> recommendActions() {
        return service.recommendActions();
    }

    @GetMapping("/agent/evidence/{messageId}")
    @Operation(summary = "AI 답변 근거 조회", description = "AI 메시지의 출처와 당시 도구 스냅샷을 반환합니다.")
    public Map<String, Object> evidence(@PathVariable long messageId) {
        return service.evidence(messageId);
    }

    @GetMapping("/budgets/monthly")
    @Operation(summary = "월 예산 조회", description = "최근 월 예산과 카테고리별 한도를 반환합니다.")
    public MonthlyBudget budget() {
        return service.getBudget();
    }

    @PutMapping("/budgets/monthly")
    @Operation(summary = "월 예산 저장", description = "월 단위 총 예산과 카테고리별 한도를 생성하거나 갱신합니다.")
    public MonthlyBudget putBudget(@Valid @RequestBody MonthlyBudgetRequest request) {
        return service.putBudget(new MonthlyBudget(request.month(), request.categoryLimits(), request.totalLimit()));
    }

    @GetMapping("/weather-statuses")
    @Operation(summary = "자금 날씨 상태 안내", description = "SUNNY, CLOUDY, RAINY, STORM 상태별 의미와 행동 가이드를 반환합니다.")
    public Map<String, Object> weatherStatuses() {
        return service.weatherStatuses();
    }

    @GetMapping("/forecast-alerts")
    @Operation(summary = "예측 위험 알림 조회", description = "현재 대시보드 기준 위험 요약을 알림 형태로 반환합니다.")
    public Map<String, Object> forecastAlerts() {
        return service.forecastAlerts();
    }

    public record SeedRequest(@NotNull Long userId, @NotNull @DateTimeFormat(pattern = "yyyy-MM") YearMonth baseMonth, boolean reset) {}
    public record CategoryPatch(@NotNull Long categoryId) {}
    public record EventCreateRequest(@NotNull LocalDate eventDate, @NotBlank String title, @Positive long amount, @NotNull Direction direction, @NotNull EventType eventType, boolean fixed) {}
    public record RecurringCreateRequest(@NotBlank String title, @NotNull RecurrenceType recurrenceType, Integer dayOfMonth, @NotNull LocalDate startDate, LocalDate endDate, @Positive long amount, @NotNull EventType eventType, @NotNull Direction direction) {}
    public record SimulationCreateRequest(@NotNull LocalDate spendingDate, @Positive long amount, @NotNull LocalDate targetDate, String title) {}
    public record ConversationCreateRequest(String title) {}
    public record MonthlyBudgetRequest(@NotNull @DateTimeFormat(pattern = "yyyy-MM") YearMonth month, @NotNull Map<String, Integer> categoryLimits, @Positive int totalLimit) {}
}
