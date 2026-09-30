package com.moneyweather.service;

import com.moneyweather.domain.DefaultCategories;
import com.moneyweather.domain.Enums.*;
import com.moneyweather.domain.entity.*;
import com.moneyweather.repository.*;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 개발·시연용 데모 계정과 샘플 데이터. */
@Service
@Transactional
public class DemoDataService {
    private static final long DEFAULT_USER_ID = 1L;
    /** 데모 계정. 비밀번호는 {@code money-weather.dev.demo-password}(기본 demo1234!)로 정한다. */
    public static final String DEMO_EMAIL = "demo@moneyweather.dev";

    private final UserRepository users;
    private final AccountRepository accounts;
    private final CardRepository cards;
    private final CategoryRepository categories;
    private final TransactionRepository transactions;
    private final FinancialEventRepository events;
    private final RecurringRuleRepository rules;
    private final MonthlyBudgetRepository budgets;
    private final BudgetCategoryLimitRepository budgetLimits;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final PasswordEncoder passwordEncoder;
    private final String demoPassword;
    private final boolean seedEnabled;
    private final boolean demoDataEnabled;

    public DemoDataService(UserRepository users, AccountRepository accounts, CardRepository cards, CategoryRepository categories,
                           TransactionRepository transactions, FinancialEventRepository events, RecurringRuleRepository rules,
                           MonthlyBudgetRepository budgets, BudgetCategoryLimitRepository budgetLimits,
                           ConversationRepository conversations, MessageRepository messages, PasswordEncoder passwordEncoder,
                           @Value("${money-weather.dev.demo-password:demo1234!}") String demoPassword,
                           @Value("${money-weather.dev.seed-enabled:true}") boolean seedEnabled,
                           @Value("${money-weather.dev.demo-data-enabled:true}") boolean demoDataEnabled) {
        this.users = users;
        this.accounts = accounts;
        this.cards = cards;
        this.categories = categories;
        this.transactions = transactions;
        this.events = events;
        this.rules = rules;
        this.budgets = budgets;
        this.budgetLimits = budgetLimits;
        this.conversations = conversations;
        this.messages = messages;
        this.passwordEncoder = passwordEncoder;
        this.demoPassword = demoPassword;
        this.seedEnabled = seedEnabled;
        this.demoDataEnabled = demoDataEnabled;
    }

    /**
     * DB 가 비어 있으면 데모 계정과 데이터를 만든다. 이미 사용자가 있으면 아무것도 하지 않는다.
     * 운영(postgres 프로필)에서는 기본으로 꺼져 있고, 시연용으로 {@code DEMO_DATA_ENABLED=true}로 켤 수 있다.
     */
    @PostConstruct
    void init() {
        if (demoDataEnabled && users.count() == 0) {
            createDemoData(DEFAULT_USER_ID, YearMonth.of(2026, 9));
        }
    }

    /**
     * 개발·시연용 데이터 초기화. <b>모든 사용자의 데이터를 지우고</b> 데모 계정 하나를 다시 만든다.
     * 운영에서는 {@code money-weather.dev.seed-enabled=false}로 꺼서 404 를 돌려준다.
     */
    public Map<String, Object> seed(Long userId, YearMonth baseMonth, boolean reset) {
        if (!seedEnabled) {
            throw Errors.notFound("Seeding is disabled.");
        }
        Long id = userId == null ? DEFAULT_USER_ID : userId;
        if (!reset && users.existsById(id)) {
            throw Errors.conflict("Seed data already exists.");
        }
        return createDemoData(id, baseMonth);
    }

    /** 모든 데이터를 지우고 데모 계정과 데이터를 만든다. */
    private Map<String, Object> createDemoData(Long id, YearMonth baseMonth) {
        clearAll();
        users.save(new UserEntity(id, "테스트 사용자", baseMonth.toString(), UserStatus.ACTIVE, DEMO_EMAIL, passwordEncoder.encode(demoPassword)));

        List<AccountEntity> savedAccounts = accounts.saveAll(List.of(
                new AccountEntity(id, "하나은행", "생활비 통장", 800_000, "생활비", true),
                new AccountEntity(id, "국민은행", "비상금", 400_000, "예비비", true),
                new AccountEntity(id, "카카오뱅크", "여행 적금", 1_000_000, "목적자금", false)
        ));
        // 고정 지출과 카드 대금은 모두 생활비 통장에서 나간다.
        Long livingAccountId = savedAccounts.getFirst().getId();
        List<CardEntity> savedCards = cards.saveAll(List.of(
                new CardEntity(id, "신한카드", "Deep Dream", 5, true, livingAccountId),
                new CardEntity(id, "현대카드", "Zero", 15, true, livingAccountId)
        ));
        Long shinhanCardId = savedCards.getFirst().getId();
        List<CategoryEntity> savedCategories = categories.saveAll(DefaultCategories.forUser(id));
        Map<String, Long> categoryIds = savedCategories.stream().collect(Collectors.toMap(CategoryEntity::getName, CategoryEntity::getId));
        transactions.saveAll(List.of(
                // 8월 신한카드 사용분 합계 520,000 -> 9/5 카드 청구 이벤트로 자동 생성된다.
                new TransactionEntity(id, LocalDate.of(2026, 8, 8), "이마트", 240_000, TransactionType.EXPENSE, categoryIds.get("식비/카페"), shinhanCardId),
                new TransactionEntity(id, LocalDate.of(2026, 8, 17), "주유소", 180_000, TransactionType.EXPENSE, categoryIds.get("교통"), shinhanCardId),
                new TransactionEntity(id, LocalDate.of(2026, 8, 24), "배달앱", 100_000, TransactionType.EXPENSE, categoryIds.get("식비/카페"), shinhanCardId),
                new TransactionEntity(id, LocalDate.of(2026, 9, 3), "스타벅스", 6500, TransactionType.EXPENSE, categoryIds.get("식비/카페")),
                new TransactionEntity(id, LocalDate.of(2026, 9, 4), "지하철", 1500, TransactionType.EXPENSE, categoryIds.get("교통")),
                new TransactionEntity(id, LocalDate.of(2026, 9, 25), "회사 급여", 2_500_000, TransactionType.INCOME, categoryIds.get("급여"))
        ));
        // 6~8월 정기 결제 이력. 넷플릭스·유튜브 프리미엄은 반복 규칙이 없어 '반복 지출 후보'로 탐지되고,
        // 통신비는 이미 규칙으로 등록돼 있어 후보에서 빠진다. 카드와 연결하지 않아 9월 카드 청구액에는 영향이 없다.
        List<TransactionEntity> history = new ArrayList<>();
        int[] youtubeDays = {20, 21, 19};
        for (int i = 0; i < 3; i++) {
            int month = 6 + i;
            history.add(new TransactionEntity(id, LocalDate.of(2026, month, 15), "넷플릭스", 39_000, TransactionType.EXPENSE, categoryIds.get("구독")));
            history.add(new TransactionEntity(id, LocalDate.of(2026, month, youtubeDays[i]), "유튜브 프리미엄", 14_900, TransactionType.EXPENSE, categoryIds.get("구독")));
            history.add(new TransactionEntity(id, LocalDate.of(2026, month, 10), "통신비", 80_000, TransactionType.EXPENSE, null));
        }
        transactions.saveAll(history);
        // 카드 결제(9/5)와 통신비(9/10)는 각각 카드 결제일과 반복 규칙에서 자동 생성되므로 여기서 심지 않는다.
        // 시드 거래는 이미 잔액에 반영된 과거 기록으로 보고 계좌를 연결하지 않는다.
        events.saveAll(List.of(
                new FinancialEventEntity(id, LocalDate.of(2026, 9, 15), "넷플릭스", 39_000, Direction.OUTFLOW, EventType.SUBSCRIPTION, EventStatus.SCHEDULED, true, null, null, livingAccountId),
                new FinancialEventEntity(id, LocalDate.of(2026, 9, 25), "급여", 2_500_000, Direction.INFLOW, EventType.SALARY, EventStatus.SCHEDULED, true, null, null, livingAccountId)
        ));
        rules.save(new RecurringRuleEntity(id, "통신비", RecurrenceType.MONTHLY, 10, LocalDate.of(2026, 9, 1), null, 80_000, EventType.TELECOM, Direction.OUTFLOW, true, livingAccountId));
        MonthlyBudgetEntity budget = budgets.save(new MonthlyBudgetEntity(id, baseMonth.toString(), 530_000));
        budgetLimits.saveAll(List.of(
                new BudgetCategoryLimitEntity(budget.getId(), "식비/카페", 350_000),
                new BudgetCategoryLimitEntity(budget.getId(), "교통", 100_000),
                new BudgetCategoryLimitEntity(budget.getId(), "구독", 80_000)
        ));

        return Map.of("userId", id, "baseMonth", baseMonth.toString(), "created", Map.of(
                "accounts", accounts.findByUserId(id).size(),
                "cards", savedCards.size(),
                "transactions", transactions.findByUserIdAndTransactionDateBetween(id, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 9, 30)).size(),
                "financialEvents", events.findByUserId(id).size(),
                "recurringRules", rules.findByUserId(id).size()));
    }

    /**
     * 외래 키를 참조하는 쪽부터 지운다.
     * financial_events 는 recurring_rules 와 cards 를, transactions 는 categories 와 cards 를 참조하므로
     * 이 둘을 참조 대상보다 먼저 지워야 한다.
     */
    private void clearAll() {
        messages.deleteAll();
        conversations.deleteAll();
        budgetLimits.deleteAll();
        budgets.deleteAll();
        events.deleteAll();
        transactions.deleteAll();
        rules.deleteAll();
        categories.deleteAll();
        cards.deleteAll();
        accounts.deleteAll();
        users.deleteAll();
    }
}
