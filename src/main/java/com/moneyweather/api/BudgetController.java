package com.moneyweather.api;

import com.moneyweather.service.BudgetService;
import com.moneyweather.service.BudgetService.MonthlyBudget;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/budgets/monthly")
@Tag(name = "Budgets", description = "월 예산과 한도 대비 지출")
public class BudgetController {
    private final BudgetService service;

    public BudgetController(BudgetService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "월 예산 조회", description = "지정한 월의 예산과 카테고리별 한도를 반환합니다. month를 생략하면 최근 월을 반환합니다.")
    public MonthlyBudget get(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return service.get(month);
    }

    @GetMapping("/status")
    @Operation(summary = "월 예산 집행 현황 조회", description = "카테고리별 한도와 실제 지출을 대조해 잔여 한도와 초과 여부를 반환합니다.")
    public Map<String, Object> status(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return service.status(month);
    }

    @PutMapping
    @Operation(summary = "월 예산 저장", description = "월 단위 총 예산과 카테고리별 한도를 생성하거나 갱신합니다.")
    public MonthlyBudget put(@Valid @RequestBody MonthlyBudgetRequest request) {
        return service.put(new MonthlyBudget(request.month(), request.categoryLimits(), request.totalLimit()));
    }

    public record MonthlyBudgetRequest(@NotNull @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                                       @NotNull Map<String, Integer> categoryLimits, @Positive int totalLimit) {}
}
