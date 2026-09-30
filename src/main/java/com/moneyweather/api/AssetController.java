package com.moneyweather.api;

import com.moneyweather.domain.Enums.CategoryType;
import com.moneyweather.service.AssetService;
import com.moneyweather.service.AssetService.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Assets", description = "계좌·카드·카테고리 조회, 등록, 수정, 삭제")
public class AssetController {
    private final AssetService service;

    public AssetController(AssetService service) {
        this.service = service;
    }

    @GetMapping("/accounts")
    @Operation(summary = "계좌 목록 및 잔액 조회", description = "자산 포함 여부 필터를 적용해 계좌 목록과 총 잔액을 반환합니다.")
    public Map<String, Object> accounts(@RequestParam(defaultValue = "false") boolean includedOnly) {
        return service.accounts(includedOnly);
    }

    @PostMapping("/accounts")
    @Operation(summary = "계좌 등록", description = "잔액을 생략하면 0원으로, 자산 포함 여부를 생략하면 포함으로 등록합니다.")
    public Account createAccount(@Valid @RequestBody AccountCreateRequest request) {
        return service.createAccount(new AccountMutation(request.bankName(), request.accountName(), request.balance(), request.purpose(), request.includedInAssets()));
    }

    @PatchMapping("/accounts/{accountId}")
    @Operation(summary = "계좌 수정 / 잔액 직접 수정", description = "balance를 넘기면 실제 통장 잔액에 맞춰 덮어씁니다. 거래·결제로 인한 잔액 변화는 자동으로 반영되므로 어긋났을 때만 사용합니다.")
    public Account updateAccount(@PathVariable long accountId, @Valid @RequestBody AccountUpdateRequest request) {
        return service.updateAccount(accountId, new AccountPatch(request.bankName(), request.accountName(), request.balance(), request.purpose(), request.includedInAssets()));
    }

    @DeleteMapping("/accounts/{accountId}")
    @Operation(summary = "계좌 삭제", description = "거래·예정 이벤트·카드·반복 규칙이 이 계좌를 쓰고 있으면 409를 반환합니다.")
    public Map<String, Object> deleteAccount(@PathVariable long accountId) {
        return service.deleteAccount(accountId);
    }

    @GetMapping("/cards")
    @Operation(summary = "카드 목록 조회", description = "사용자의 카드사, 카드명, 결제일, 결제 계좌, 활성 상태를 반환합니다.")
    public Map<String, Object> cards(@RequestParam(required = false) Boolean active) {
        return service.cards(active);
    }

    @PostMapping("/cards")
    @Operation(summary = "카드 등록", description = "결제일(1~31)과 대금이 빠져나갈 결제 계좌를 지정합니다.")
    public Card createCard(@Valid @RequestBody CardCreateRequest request) {
        return service.createCard(new CardMutation(request.cardCompany(), request.cardName(), request.paymentDay(), request.paymentAccountId()));
    }

    @PatchMapping("/cards/{cardId}")
    @Operation(summary = "카드 수정", description = "카드명, 결제일, 결제 계좌, 활성 상태를 수정합니다. 아직 지나지 않은 청구 이벤트는 다음 조회 때 새 값으로 맞춰집니다.")
    public Card updateCard(@PathVariable long cardId, @Valid @RequestBody CardUpdateRequest request) {
        return service.updateCard(cardId, new CardPatch(request.cardCompany(), request.cardName(), request.paymentDay(), request.active(), request.paymentAccountId()));
    }

    @DeleteMapping("/cards/{cardId}")
    @Operation(summary = "카드 비활성화", description = "과거 거래와 청구 이력이 남아 있으므로 삭제하지 않고 비활성화합니다. 비활성 카드는 새 청구가 생기지 않습니다.")
    public Map<String, Object> deleteCard(@PathVariable long cardId) {
        return service.deleteCard(cardId);
    }

    @GetMapping("/categories")
    @Operation(summary = "카테고리 목록 조회", description = "거래 분류에 사용하는 수입, 지출, 이체 카테고리를 반환합니다.")
    public Map<String, Object> categories(@RequestParam(required = false) CategoryType categoryType) {
        return service.categories(categoryType);
    }

    @PostMapping("/categories")
    @Operation(summary = "카테고리 등록", description = "같은 이름의 카테고리가 있으면 409를 반환합니다.")
    public Category createCategory(@Valid @RequestBody CategoryCreateRequest request) {
        return service.createCategory(new CategoryMutation(request.name(), request.categoryType()));
    }

    @PatchMapping("/categories/{categoryId}")
    @Operation(summary = "카테고리 수정", description = "이름을 바꾸면 이 카테고리에 걸린 예산 한도도 새 이름으로 함께 바뀝니다.")
    public Category updateCategory(@PathVariable long categoryId, @Valid @RequestBody CategoryUpdateRequest request) {
        return service.updateCategory(categoryId, new CategoryPatch(request.name(), request.categoryType()));
    }

    @DeleteMapping("/categories/{categoryId}")
    @Operation(summary = "카테고리 삭제", description = "기본 카테고리는 삭제할 수 없습니다(409). 삭제하면 해당 거래는 미분류가 되고, 이 카테고리의 예산 한도도 함께 삭제됩니다.")
    public Map<String, Object> deleteCategory(@PathVariable long categoryId) {
        return service.deleteCategory(categoryId);
    }

    public record AccountCreateRequest(@NotBlank String bankName, @NotBlank String accountName, Long balance, String purpose, Boolean includedInAssets) {}
    public record AccountUpdateRequest(@Size(min = 1) String bankName, @Size(min = 1) String accountName, Long balance, String purpose, Boolean includedInAssets) {}
    public record CardCreateRequest(@NotBlank String cardCompany, @NotBlank String cardName, @Min(1) @Max(31) int paymentDay, Long paymentAccountId) {}
    public record CardUpdateRequest(@Size(min = 1) String cardCompany, @Size(min = 1) String cardName, @Min(1) @Max(31) Integer paymentDay, Boolean active, Long paymentAccountId) {}
    public record CategoryCreateRequest(@NotBlank String name, @NotNull CategoryType categoryType) {}
    public record CategoryUpdateRequest(@Size(min = 1) String name, CategoryType categoryType) {}
}
