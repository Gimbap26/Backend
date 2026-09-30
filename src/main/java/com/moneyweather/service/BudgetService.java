package com.moneyweather.service;

import com.moneyweather.domain.Enums.TransactionType;
import com.moneyweather.domain.entity.*;
import com.moneyweather.repository.BudgetCategoryLimitRepository;
import com.moneyweather.repository.CategoryRepository;
import com.moneyweather.repository.MonthlyBudgetRepository;
import com.moneyweather.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/** 월 예산과 소비 집계. 예산 대조와 소비 분석이 같은 집계({@link #expenseByCategory})를 쓴다. */
@Service
@Transactional
public class BudgetService {
    private final MonthlyBudgetRepository budgets;
    private final BudgetCategoryLimitRepository budgetLimits;
    private final TransactionRepository transactions;
    private final CategoryRepository categories;
    private final CurrentUserService currentUser;

    public BudgetService(MonthlyBudgetRepository budgets, BudgetCategoryLimitRepository budgetLimits,
                         TransactionRepository transactions, CategoryRepository categories, CurrentUserService currentUser) {
        this.budgets = budgets;
        this.budgetLimits = budgetLimits;
        this.transactions = transactions;
        this.categories = categories;
        this.currentUser = currentUser;
    }

    public record MonthlyBudget(YearMonth month, Map<String, Integer> categoryLimits, int totalLimit) {}

    /** 달을 생략하면 가장 최근 예산. */
    @Transactional(readOnly = true)
    public MonthlyBudget get(YearMonth month) {
        return toBudget(requireBudget(currentUser.requireId(), month));
    }

    public MonthlyBudget put(MonthlyBudget budget) {
        Long userId = currentUser.requireId();
        MonthlyBudgetEntity entity = budgets.findByUserIdAndBudgetMonth(userId, budget.month().toString())
                .orElseGet(() -> budgets.save(new MonthlyBudgetEntity(userId, budget.month().toString(), budget.totalLimit())));
        entity.update(budget.month().toString(), budget.totalLimit());
        budgetLimits.deleteByBudgetId(entity.getId());
        budgetLimits.saveAll(budget.categoryLimits().entrySet().stream()
                .map(entry -> new BudgetCategoryLimitEntity(entity.getId(), entry.getKey(), entry.getValue()))
                .toList());
        return toBudget(entity);
    }

    /**
     * 예산이 있는지. 없을 때 {@link #status}를 부르면 404 를 던지는데, 다른 트랜잭션 안에서 그 예외를 잡아 넘기면
     * 트랜잭션이 롤백 전용으로 표시돼 커밋 때 실패하므로, 부르는 쪽은 먼저 이것으로 확인한다.
     */
    @Transactional(readOnly = true)
    public boolean exists(YearMonth month) {
        return budgets.findByUserIdAndBudgetMonth(currentUser.requireId(), month.toString()).isPresent();
    }

    /** 한도 대비 실제 지출을 카테고리별로 대조한다. */
    @Transactional(readOnly = true)
    public Map<String, Object> status(YearMonth month) {
        Long userId = currentUser.requireId();
        MonthlyBudgetEntity budget = requireBudget(userId, month);
        Map<String, Long> spentByCategory = expenseByCategory(userId, YearMonth.parse(budget.getBudgetMonth()));
        Map<String, Integer> limits = toBudget(budget).categoryLimits();

        // 한도가 걸린 카테고리와 실제 지출이 있는 카테고리를 합쳐, 어느 쪽이든 빠지지 않게 한다.
        Set<String> names = new TreeSet<>(limits.keySet());
        names.addAll(spentByCategory.keySet());

        List<Map<String, Object>> rows = names.stream().map(name -> {
            Integer limit = limits.get(name);
            long spent = spentByCategory.getOrDefault(name, 0L);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("category", name);
            row.put("limit", limit);
            row.put("spent", spent);
            row.put("remaining", limit == null ? null : limit - spent);
            row.put("exceeded", limit != null && spent > limit);
            return row;
        }).toList();

        long totalSpent = spentByCategory.values().stream().mapToLong(Long::longValue).sum();
        int totalLimit = budget.getTotalLimitAmount();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("month", budget.getBudgetMonth());
        body.put("totalLimit", totalLimit);
        body.put("totalSpent", totalSpent);
        body.put("totalRemaining", totalLimit - totalSpent);
        body.put("totalExceeded", totalSpent > totalLimit);
        body.put("categories", rows);
        return body;
    }

    /** 해당 월 지출을 카테고리별로 합산하고 많이 쓴 순서로 정렬한다. 생략하면 기준 월. */
    @Transactional(readOnly = true)
    public Map<String, Object> spendingSummary(YearMonth month) {
        UserEntity user = currentUser.require();
        YearMonth target = month != null ? month : YearMonth.parse(user.getBaseMonth());
        Map<String, Long> byCategory = expenseByCategory(user.getId(), target);
        long total = byCategory.values().stream().mapToLong(Long::longValue).sum();
        List<Map<String, Object>> ranked = byCategory.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> Map.<String, Object>of("category", e.getKey(), "amount", e.getValue(),
                        "share", total == 0 ? 0 : Math.round(e.getValue() * 1000.0 / total) / 10.0))
                .toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("month", target.toString());
        body.put("totalExpense", total);
        body.put("byCategory", byCategory);
        body.put("ranking", ranked);
        return body;
    }

    /** 해당 월 지출 거래를 카테고리 이름으로 집계한다. */
    private Map<String, Long> expenseByCategory(Long userId, YearMonth month) {
        Map<Long, CategoryEntity> categoryMap = categories.mapByIdForUser(userId);
        return transactions.findByUserIdAndTransactionDateBetween(userId, month.atDay(1), month.atEndOfMonth()).stream()
                .filter(t -> t.getTransactionType() == TransactionType.EXPENSE)
                .collect(Collectors.groupingBy(
                        t -> CategoryRepository.nameOf(categoryMap, t.getCategoryId()),
                        Collectors.summingLong(TransactionEntity::getAmount)));
    }

    private MonthlyBudgetEntity requireBudget(Long userId, YearMonth month) {
        Optional<MonthlyBudgetEntity> found = month == null
                ? budgets.findFirstByUserIdOrderByBudgetMonthDesc(userId)
                : budgets.findByUserIdAndBudgetMonth(userId, month.toString());
        return found.orElseThrow(() -> Errors.notFound("Monthly budget not found."));
    }

    private MonthlyBudget toBudget(MonthlyBudgetEntity budget) {
        Map<String, Integer> limits = budgetLimits.findByBudgetId(budget.getId()).stream()
                .collect(Collectors.toMap(BudgetCategoryLimitEntity::getCategoryName, BudgetCategoryLimitEntity::getLimitAmount));
        return new MonthlyBudget(YearMonth.parse(budget.getBudgetMonth()), limits, budget.getTotalLimitAmount());
    }
}
