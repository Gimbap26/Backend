package com.moneyweather.api;

import com.moneyweather.service.AgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/agent")
@Tag(name = "AI Agent", description = "도구를 호출해 사용자 데이터를 근거로 답하는 AI 상담, 답변 근거, 추천 질문과 행동 추천")
public class AgentController {
    private final AgentService service;

    public AgentController(AgentService service) {
        this.service = service;
    }

    @PostMapping("/conversations")
    @Operation(summary = "AI 대화 생성", description = "대화 세션을 만들고 conversationId를 반환합니다.")
    public Map<String, Object> startConversation(@RequestBody(required = false) ConversationCreateRequest request) {
        return service.startConversation(request == null ? null : request.title());
    }

    @PostMapping("/chat")
    @Operation(summary = "AI Agent 질문", description = "AI가 질문에 필요한 도구(대시보드, 예측, 예산, 소비 분석, 시뮬레이션 등)를 골라 호출하고 그 결과로 답합니다. "
            + "conversationId를 생략하면 새 대화를 만듭니다. 응답의 provider가 '-fallback'으로 끝나면 외부 AI 호출에 실패해 규칙 기반으로 답한 것입니다.")
    public Map<String, Object> chat(@Valid @RequestBody ChatRequest request) {
        return service.chat(request.conversationId(), request.message());
    }

    @GetMapping("/conversations/{id}/messages")
    @Operation(summary = "AI 대화 메시지 조회", description = "대화에 저장된 사용자/AI 메시지를 시간순으로 반환합니다.")
    public Map<String, Object> messages(@PathVariable long id) {
        return service.messages(id);
    }

    @GetMapping("/evidence/{messageId}")
    @Operation(summary = "AI 답변 근거 조회", description = "답변 당시 AI가 호출한 도구와 인자, 결과를 저장된 그대로 반환합니다.")
    public Map<String, Object> evidence(@PathVariable long messageId) {
        return service.evidence(messageId);
    }

    @GetMapping("/suggestions")
    @Operation(summary = "AI 추천 질문 조회", description = "현재 잔액 흐름, 예산, 반복 지출 상황에 맞춰 물어볼 만한 질문 4개를 반환합니다.")
    public Map<String, Object> suggestions() {
        return service.suggestions();
    }

    @PostMapping("/analyze-spending")
    @Operation(summary = "소비 패턴 분석", description = "월 지출을 카테고리별로 합산하고 많이 쓴 순서로 정렬합니다. month를 생략하면 기준 월.")
    public Map<String, Object> analyzeSpending(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return service.analyzeSpending(month);
    }

    @PostMapping("/detect-recurring")
    @Operation(summary = "반복 지출 후보 탐지", description = "최근 6개월 거래에서 3개월 이상 비슷한 금액으로 반복됐지만 반복 규칙으로 등록되지 않은 지출을 찾습니다. "
            + "각 후보의 suggestedRule은 POST /recurring-rules에 그대로 보낼 수 있습니다.")
    public Map<String, Object> detectRecurring() {
        return service.detectRecurring();
    }

    @PostMapping("/recommend-actions")
    @Operation(summary = "행동 추천", description = "하루 사용 가능 금액, 잔액이 가장 낮은 날, 예산 초과, 다가오는 결제, 등록할 반복 지출을 급한 순서로 추천합니다.")
    public Map<String, Object> recommendActions() {
        return service.recommendActions();
    }

    public record ConversationCreateRequest(String title) {}
    public record ChatRequest(Long conversationId, @NotBlank @Size(max = 2000) String message) {}
}
