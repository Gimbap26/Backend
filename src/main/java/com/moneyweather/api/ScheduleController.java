package com.moneyweather.api;

import com.moneyweather.domain.Enums.Direction;
import com.moneyweather.domain.Enums.EventStatus;
import com.moneyweather.domain.Enums.EventType;
import com.moneyweather.domain.Enums.RecurrenceType;
import com.moneyweather.service.ScheduleService;
import com.moneyweather.service.ScheduleService.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Schedule", description = "앞으로의 현금 흐름: 예정 이벤트와 반복 규칙")
public class ScheduleController {
    private final ScheduleService service;

    public ScheduleController(ScheduleService service) {
        this.service = service;
    }

    @GetMapping("/financial-events")
    @Operation(summary = "예정 금융 이벤트 조회", description = "조회 기간, 방향, 이벤트 유형, 상태 조건으로 예정 지출/입금 이벤트를 반환합니다.")
    public Map<String, Object> events(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                      @RequestParam(required = false) Direction direction,
                                      @RequestParam(required = false) EventType eventType,
                                      @RequestParam(required = false) EventStatus status) {
        return service.events(from, to, direction, eventType, status);
    }

    @PostMapping("/financial-events")
    @Operation(summary = "예정 금융 이벤트 등록", description = "카드 결제, 통신비, 구독, 급여 등 미래 현금흐름 이벤트를 등록합니다.")
    public FinancialEvent createEvent(@Valid @RequestBody EventCreateRequest request) {
        return service.createEvent(new EventMutation(request.eventDate(), request.title(), request.amount(), request.direction(),
                request.eventType(), request.fixed(), request.accountId()));
    }

    @PatchMapping("/financial-events/{eventId}")
    @Operation(summary = "예정 금융 이벤트 수정", description = "이벤트 일자, 제목, 금액, 상태, 고정 지출 여부를 수정합니다.")
    public Map<String, Object> updateEvent(@PathVariable long eventId, @Valid @RequestBody EventPatch request) {
        return service.updateEvent(eventId, new ScheduleService.EventPatch(request.eventDate(), request.title(), request.amount(),
                request.status(), request.fixed(), request.accountId()));
    }

    @DeleteMapping("/financial-events/{eventId}")
    @Operation(summary = "예정 금융 이벤트 취소", description = "이벤트를 삭제하지 않고 CANCELED 상태로 변경합니다.")
    public Map<String, Object> cancelEvent(@PathVariable long eventId) {
        return service.cancelEvent(eventId);
    }

    @PostMapping("/recurring-rules")
    @Operation(summary = "반복 규칙 생성", description = "월 반복 지출/입금 규칙을 생성하고 해당 월 예정 이벤트를 함께 생성합니다.")
    public Map<String, Object> createRule(@Valid @RequestBody RecurringCreateRequest request) {
        RuleCreation created = service.createRule(new RecurringMutation(request.title(), request.recurrenceType(), request.dayOfMonth(),
                request.startDate(), request.endDate(), request.amount(), request.eventType(), request.direction(), request.accountId()));
        RecurringRule rule = created.rule();
        return Map.of("recurringRuleId", rule.recurringRuleId(), "title", rule.title(), "recurrenceType", rule.recurrenceType(),
                "generatedEvents", created.generatedEvents());
    }

    @GetMapping("/recurring-rules")
    @Operation(summary = "반복 규칙 목록 조회", description = "등록된 반복 지출/입금 규칙 목록을 반환합니다.")
    public Map<String, Object> rules() {
        return service.rules();
    }

    @PatchMapping("/recurring-rules/{id}")
    @Operation(summary = "반복 규칙 수정", description = "반복 규칙의 제목, 금액, 일자, 활성 상태 등을 수정합니다.")
    public RecurringRule updateRule(@PathVariable long id, @Valid @RequestBody RecurringPatch request) {
        return service.updateRule(id, new ScheduleService.RecurringPatch(request.title(), request.recurrenceType(), request.dayOfMonth(),
                request.startDate(), request.endDate(), request.amount(), request.eventType(), request.direction(), request.active(), request.accountId()));
    }

    @DeleteMapping("/recurring-rules/{id}")
    @Operation(summary = "반복 규칙 비활성화", description = "반복 규칙을 삭제하지 않고 비활성 상태로 변경합니다.")
    public Map<String, Object> deleteRule(@PathVariable long id) {
        return service.deleteRule(id);
    }

    public record EventCreateRequest(@NotNull LocalDate eventDate, @NotBlank String title, @Positive long amount, @NotNull Direction direction,
                                     @NotNull EventType eventType, boolean fixed, Long accountId) {}
    public record RecurringCreateRequest(@NotBlank String title, @NotNull RecurrenceType recurrenceType, @Min(1) @Max(31) Integer dayOfMonth, @NotNull LocalDate startDate,
                                         LocalDate endDate, @Positive long amount, @NotNull EventType eventType, @NotNull Direction direction, Long accountId) {}
    public record EventPatch(LocalDate eventDate, @Size(min = 1) String title, @Positive Long amount, EventStatus status, Boolean fixed, Long accountId) {}
    public record RecurringPatch(@Size(min = 1) String title, RecurrenceType recurrenceType, @Min(1) @Max(31) Integer dayOfMonth, LocalDate startDate,
                                 LocalDate endDate, @Positive Long amount, EventType eventType, Direction direction, Boolean active, Long accountId) {}
}
