package com.moneyweather.api;

import com.moneyweather.domain.Enums.TransactionType;
import com.moneyweather.service.TransactionService;
import com.moneyweather.service.TransactionService.TransactionMutation;
import com.moneyweather.service.TransactionService.TransactionPatch;
import com.moneyweather.service.TransactionService.TransactionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transactions", description = "거래 검색, 등록, 수정, 삭제. 계좌 거래는 잔액에 즉시 반영됩니다.")
public class TransactionController {
    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "거래 내역 조회", description = "월, 거래 유형, 카테고리, 키워드, 페이지 조건으로 거래 내역을 조회합니다.")
    public Map<String, Object> search(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                                      @RequestParam(required = false) TransactionType transactionType,
                                      @RequestParam(required = false) Long categoryId,
                                      @RequestParam(required = false) String keyword,
                                      @RequestParam(defaultValue = "0") @Min(0) int page,
                                      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.search(month, transactionType, categoryId, keyword, page, size);
    }

    @PostMapping
    @Operation(summary = "거래 등록", description = "실제 사용/수입 거래를 기록합니다. cardId를 지정하면 해당 카드의 결제일 청구액에 합산됩니다.")
    public TransactionView create(@Valid @RequestBody TransactionCreateRequest request) {
        return service.create(new TransactionMutation(request.transactionDate(), request.merchant(), request.amount(), request.transactionType(),
                request.categoryId(), request.cardId(), request.accountId(), request.transferAccountId()));
    }

    @PatchMapping("/{transactionId}")
    @Operation(summary = "거래 수정", description = "거래의 일자, 상호, 금액, 유형, 카테고리, 카드를 부분 수정합니다.")
    public TransactionView update(@PathVariable long transactionId, @RequestBody TransactionPatch request) {
        return service.update(transactionId, request);
    }

    @DeleteMapping("/{transactionId}")
    @Operation(summary = "거래 삭제", description = "거래를 삭제합니다. 현재 사용자 소유 거래만 삭제할 수 있습니다.")
    public Map<String, Object> delete(@PathVariable long transactionId) {
        return service.delete(transactionId);
    }

    @PatchMapping("/{transactionId}/category")
    @Operation(summary = "거래 카테고리 변경", description = "거래의 카테고리를 변경합니다. 현재 사용자 소유 거래와 카테고리만 수정할 수 있습니다.")
    public Map<String, Object> changeCategory(@PathVariable long transactionId, @Valid @RequestBody CategoryPatch request) {
        return service.changeCategory(transactionId, request.categoryId());
    }

    public record TransactionCreateRequest(@NotNull LocalDate transactionDate, @NotBlank String merchant, @Positive long amount,
                                           @NotNull TransactionType transactionType, Long categoryId, Long cardId, Long accountId, Long transferAccountId) {}
    public record CategoryPatch(@NotNull Long categoryId) {}
}
