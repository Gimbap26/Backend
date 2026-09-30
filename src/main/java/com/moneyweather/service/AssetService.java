package com.moneyweather.service;

import com.moneyweather.domain.Enums.CategoryType;
import com.moneyweather.domain.entity.*;
import com.moneyweather.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 계좌·카드·카테고리의 조회, 생성, 수정, 삭제. */
@Service
@Transactional
public class AssetService {
    private final CurrentUserService currentUser;
    private final LedgerService ledger;
    private final AccountRepository accounts;
    private final CardRepository cards;
    private final CategoryRepository categories;
    private final TransactionRepository transactions;
    private final FinancialEventRepository events;
    private final RecurringRuleRepository rules;
    private final MonthlyBudgetRepository budgets;
    private final BudgetCategoryLimitRepository budgetLimits;
    private final ForecastService forecastService;

    public AssetService(CurrentUserService currentUser, LedgerService ledger, AccountRepository accounts, CardRepository cards,
                        CategoryRepository categories, TransactionRepository transactions, FinancialEventRepository events,
                        RecurringRuleRepository rules, MonthlyBudgetRepository budgets, BudgetCategoryLimitRepository budgetLimits,
                        ForecastService forecastService) {
        this.forecastService = forecastService;
        this.currentUser = currentUser;
        this.ledger = ledger;
        this.accounts = accounts;
        this.cards = cards;
        this.categories = categories;
        this.transactions = transactions;
        this.events = events;
        this.rules = rules;
        this.budgets = budgets;
        this.budgetLimits = budgetLimits;
    }

    public record Account(Long accountId, String bankName, String accountName, long balance, String purpose, boolean includedInAssets) {}
    public record Card(Long cardId, String cardCompany, String cardName, int paymentDay, boolean active, Long paymentAccountId) {}
    public record Category(Long categoryId, String name, CategoryType categoryType, boolean systemDefault) {}

    // --- 계좌 ---------------------------------------------------------------

    /** {@code totalBalance}는 자산에 포함하는 계좌만의 합계(대시보드의 현재 잔액과 같다), {@code allAccountsBalance}는 전체 합계. */
    @Transactional(readOnly = true)
    public Map<String, Object> accounts(boolean includedOnly) {
        List<AccountEntity> found = accounts.findByUserId(currentUser.requireId());
        List<Account> rows = found.stream()
                .filter(a -> !includedOnly || a.isIncludedInAssets())
                .map(this::toAccount)
                .toList();
        return Map.of(
                "totalBalance", forecastService.currentBalance(found),
                "allAccountsBalance", found.stream().mapToLong(AccountEntity::getBalance).sum(),
                "accounts", rows);
    }

    public Account createAccount(AccountMutation request) {
        Long userId = currentUser.requireId();
        AccountEntity saved = accounts.save(new AccountEntity(userId, request.bankName(), request.accountName(),
                request.balance() == null ? 0 : request.balance(), request.purpose(),
                request.includedInAssets() == null || request.includedInAssets()));
        return toAccount(saved);
    }

    /** {@code balance}를 넘기면 실제 통장 잔액에 맞춰 직접 덮어쓴다. */
    public Account updateAccount(long accountId, AccountPatch request) {
        AccountEntity account = ledger.requireOwnedAccount(currentUser.requireId(), accountId);
        account.update(request.bankName(), request.accountName(), request.balance(), request.purpose(), request.includedInAssets());
        accounts.flush();   // 동시 수정 충돌(@Version)을 이 요청 안에서 409 로 드러낸다
        return toAccount(account);
    }

    /** 거래·이벤트·카드·반복 규칙 중 하나라도 이 계좌를 쓰고 있으면 지우지 않는다. 이력이 끊기기 때문이다. */
    public Map<String, Object> deleteAccount(long accountId) {
        AccountEntity account = ledger.requireOwnedAccount(currentUser.requireId(), accountId);
        List<String> usedBy = new ArrayList<>();
        if (transactions.existsByAccountIdOrTransferAccountId(accountId, accountId)) usedBy.add("거래");
        if (events.existsByAccountId(accountId)) usedBy.add("예정 이벤트");
        if (cards.existsByPaymentAccountId(accountId)) usedBy.add("카드 결제 계좌");
        if (rules.existsByAccountId(accountId)) usedBy.add("반복 규칙");
        if (!usedBy.isEmpty()) {
            throw Errors.conflict("이 계좌를 사용하는 " + String.join(", ", usedBy) + "이(가) 있어 삭제할 수 없습니다. 연결을 먼저 바꾸세요.");
        }
        accounts.delete(account);
        return Map.of("accountId", accountId, "deletedAt", LocalDateTime.now());
    }

    // --- 카드 ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public Map<String, Object> cards(Boolean active) {
        return Map.of("cards", cards.findByUserId(currentUser.requireId()).stream()
                .filter(c -> active == null || c.isActive() == active)
                .map(this::toCard)
                .toList());
    }

    public Card createCard(CardMutation request) {
        Long userId = currentUser.requireId();
        if (request.paymentAccountId() != null) ledger.requireOwnedAccount(userId, request.paymentAccountId());
        CardEntity saved = cards.save(new CardEntity(userId, request.cardCompany(), request.cardName(), request.paymentDay(),
                true, request.paymentAccountId()));
        return toCard(saved);
    }

    /** 결제일이나 결제 계좌를 바꾸면 아직 지나지 않은 청구 이벤트는 다음 조회 때 새 값으로 맞춰진다. */
    public Card updateCard(long cardId, CardPatch request) {
        Long userId = currentUser.requireId();
        CardEntity card = requireOwnedCard(userId, cardId);
        if (request.paymentAccountId() != null) ledger.requireOwnedAccount(userId, request.paymentAccountId());
        card.update(request.cardCompany(), request.cardName(), request.paymentDay(), request.active(), request.paymentAccountId());
        return toCard(card);
    }

    /** 과거 거래와 청구 이력이 카드를 참조하므로 지우지 않고 비활성화한다. 비활성 카드는 새 청구가 생기지 않는다. */
    public Map<String, Object> deleteCard(long cardId) {
        CardEntity card = requireOwnedCard(currentUser.requireId(), cardId);
        card.deactivate();
        return Map.of("cardId", cardId, "active", false, "deactivatedAt", LocalDateTime.now());
    }

    private CardEntity requireOwnedCard(Long userId, long cardId) {
        CardEntity card = cards.findById(cardId).orElseThrow(() -> Errors.notFound("Card not found."));
        currentUser.assertOwner(card.getUserId(), userId);
        return card;
    }

    // --- 카테고리 -----------------------------------------------------------

    @Transactional(readOnly = true)
    public Map<String, Object> categories(CategoryType categoryType) {
        Long userId = currentUser.requireId();
        List<CategoryEntity> rows = categoryType == null ? categories.findByUserId(userId) : categories.findByUserIdAndCategoryType(userId, categoryType);
        return Map.of("categories", rows.stream().map(this::toCategory).toList());
    }

    public Category createCategory(CategoryMutation request) {
        Long userId = currentUser.requireId();
        assertNameAvailable(userId, request.name());
        return toCategory(categories.save(new CategoryEntity(userId, request.name(), request.categoryType(), false)));
    }

    /** 예산 한도는 카테고리를 이름으로 참조하므로, 이름을 바꾸면 이 사용자의 모든 예산 한도도 함께 바꾼다. */
    public Category updateCategory(long categoryId, CategoryPatch request) {
        Long userId = currentUser.requireId();
        CategoryEntity category = requireOwnedCategory(userId, categoryId);
        String oldName = category.getName();
        String newName = request.name();
        if (newName != null && !newName.equals(oldName)) {
            assertNameAvailable(userId, newName);
            budgetLimitsNamed(userId, oldName).forEach(limit -> limit.renameCategory(newName));
        }
        category.update(newName, request.categoryType());
        return toCategory(category);
    }

    /**
     * 기본 카테고리는 지울 수 없다. 지운 카테고리의 거래는 '미분류'가 되고,
     * 그 카테고리에 걸려 있던 예산 한도도 함께 없앤다(대상이 사라진 한도가 남지 않도록).
     */
    public Map<String, Object> deleteCategory(long categoryId) {
        Long userId = currentUser.requireId();
        CategoryEntity category = requireOwnedCategory(userId, categoryId);
        if (category.isSystemDefault()) {
            throw Errors.conflict("기본 카테고리는 삭제할 수 없습니다.");
        }
        List<TransactionEntity> affected = transactions.findByCategoryId(categoryId);
        affected.forEach(tx -> tx.changeCategory(null));
        List<BudgetCategoryLimitEntity> limits = budgetLimitsNamed(userId, category.getName());
        budgetLimits.deleteAll(limits);
        categories.delete(category);
        return Map.of("categoryId", categoryId, "uncategorizedTransactions", affected.size(),
                "removedBudgetLimits", limits.size(), "deletedAt", LocalDateTime.now());
    }

    private CategoryEntity requireOwnedCategory(Long userId, long categoryId) {
        CategoryEntity category = categories.findById(categoryId).orElseThrow(() -> Errors.notFound("Category not found."));
        currentUser.assertOwner(category.getUserId(), userId);
        return category;
    }

    private void assertNameAvailable(Long userId, String name) {
        if (categories.existsByUserIdAndName(userId, name)) {
            throw Errors.conflict("같은 이름의 카테고리가 이미 있습니다: " + name);
        }
    }

    private List<BudgetCategoryLimitEntity> budgetLimitsNamed(Long userId, String categoryName) {
        List<Long> budgetIds = budgets.findByUserId(userId).stream().map(MonthlyBudgetEntity::getId).toList();
        if (budgetIds.isEmpty()) return List.of();
        return budgetLimits.findByBudgetIdInAndCategoryName(budgetIds, categoryName);
    }

    // --- 변환 ---------------------------------------------------------------

    private Account toAccount(AccountEntity a) {
        return new Account(a.getId(), a.getBankName(), a.getAccountName(), a.getBalance(), a.getPurpose(), a.isIncludedInAssets());
    }

    private Card toCard(CardEntity c) {
        return new Card(c.getId(), c.getCardCompany(), c.getCardName(), c.getPaymentDay(), c.isActive(), c.getPaymentAccountId());
    }

    private Category toCategory(CategoryEntity c) {
        return new Category(c.getId(), c.getName(), c.getCategoryType(), c.isSystemDefault());
    }

    public record AccountMutation(String bankName, String accountName, Long balance, String purpose, Boolean includedInAssets) {}
    public record AccountPatch(String bankName, String accountName, Long balance, String purpose, Boolean includedInAssets) {}
    public record CardMutation(String cardCompany, String cardName, int paymentDay, Long paymentAccountId) {}
    public record CardPatch(String cardCompany, String cardName, Integer paymentDay, Boolean active, Long paymentAccountId) {}
    public record CategoryMutation(String name, CategoryType categoryType) {}
    public record CategoryPatch(String name, CategoryType categoryType) {}
}
