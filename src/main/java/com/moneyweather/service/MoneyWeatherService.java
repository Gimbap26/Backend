package com.moneyweather.service;

import com.moneyweather.domain.Enums.*;
import com.moneyweather.domain.entity.*;
import com.moneyweather.agent.AgentAnswerClient;
import com.moneyweather.repository.*;
import com.moneyweather.security.UserContext;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class MoneyWeatherService {
    private static final long DEFAULT_USER_ID = 1L;

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
    private final ForecastService forecastService;
    private final AgentAnswerClient agentAnswerClient;

    public MoneyWeatherService(
            UserRepository users,
            AccountRepository accounts,
            CardRepository cards,
            CategoryRepository categories,
            TransactionRepository transactions,
            FinancialEventRepository events,
            RecurringRuleRepository rules,
            MonthlyBudgetRepository budgets,
            BudgetCategoryLimitRepository budgetLimits,
            ConversationRepository conversations,
            MessageRepository messages,
            ForecastService forecastService,
            AgentAnswerClient agentAnswerClient
    ) {
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
        this.forecastService = forecastService;
        this.agentAnswerClient = agentAnswerClient;
    }

    @PostConstruct
    void init() {
        if (users.count() == 0) {
            seed(DEFAULT_USER_ID, YearMonth.of(2026, 9), true);
        }
    }

    public User getUser(Long userId) {
        UserEntity user = requireUser(userId);
        return new User(user.getId(), user.getName(), YearMonth.parse(user.getBaseMonth()), user.getStatus());
    }

    public Map<String, Object> seed(Long userId, YearMonth baseMonth, boolean reset) {
        Long id = userId == null ? DEFAULT_USER_ID : userId;
        if (!reset && users.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Seed data already exists.");
        }
        clearAll();
        users.save(new UserEntity(id, "테스트 사용자", baseMonth.toString(), UserStatus.ACTIVE));

        accounts.saveAll(List.of(
                new AccountEntity(id, "하나은행", "생활비 통장", 800_000, "생활비", true),
                new AccountEntity(id, "국민은행", "비상금", 400_000, "예비비", true),
                new AccountEntity(id, "카카오뱅크", "여행 적금", 1_000_000, "목적자금", false)
        ));
        cards.saveAll(List.of(
                new CardEntity(id, "신한카드", "Deep Dream", 5, true),
                new CardEntity(id, "현대카드", "Zero", 15, true)
        ));
        List<CategoryEntity> savedCategories = categories.saveAll(List.of(
                new CategoryEntity(id, "식비/카페", CategoryType.EXPENSE, true),
                new CategoryEntity(id, "교통", CategoryType.EXPENSE, true),
                new CategoryEntity(id, "급여", CategoryType.INCOME, true),
                new CategoryEntity(id, "구독", CategoryType.EXPENSE, true)
        ));
        Map<String, Long> categoryIds = savedCategories.stream().collect(Collectors.toMap(CategoryEntity::getName, CategoryEntity::getId));
        transactions.saveAll(List.of(
                new TransactionEntity(id, LocalDate.of(2026, 9, 3), "스타벅스", 6500, TransactionType.EXPENSE, categoryIds.get("식비/카페")),
                new TransactionEntity(id, LocalDate.of(2026, 9, 4), "지하철", 1500, TransactionType.EXPENSE, categoryIds.get("교통")),
                new TransactionEntity(id, LocalDate.of(2026, 9, 25), "회사 급여", 2_500_000, TransactionType.INCOME, categoryIds.get("급여"))
        ));
        events.saveAll(List.of(
                new FinancialEventEntity(id, LocalDate.of(2026, 9, 5), "카드 결제", 520_000, Direction.OUTFLOW, EventType.CARD_BILL, EventStatus.SCHEDULED, true),
                new FinancialEventEntity(id, LocalDate.of(2026, 9, 10), "통신비", 80_000, Direction.OUTFLOW, EventType.TELECOM, EventStatus.SCHEDULED, true),
                new FinancialEventEntity(id, LocalDate.of(2026, 9, 15), "넷플릭스", 39_000, Direction.OUTFLOW, EventType.SUBSCRIPTION, EventStatus.SCHEDULED, true),
                new FinancialEventEntity(id, LocalDate.of(2026, 9, 25), "급여", 2_500_000, Direction.INFLOW, EventType.SALARY, EventStatus.SCHEDULED, true)
        ));
        rules.save(new RecurringRuleEntity(id, "통신비", RecurrenceType.MONTHLY, 10, LocalDate.of(2026, 9, 1), null, 80_000, EventType.TELECOM, Direction.OUTFLOW, true));
        MonthlyBudgetEntity budget = budgets.save(new MonthlyBudgetEntity(id, baseMonth.toString(), 530_000));
        budgetLimits.saveAll(List.of(
                new BudgetCategoryLimitEntity(budget.getId(), "식비/카페", 350_000),
                new BudgetCategoryLimitEntity(budget.getId(), "교통", 100_000),
                new BudgetCategoryLimitEntity(budget.getId(), "구독", 80_000)
        ));

        return Map.of("userId", id, "baseMonth", baseMonth.toString(), "created", Map.of("accounts", 3, "cards", 2, "transactions", 3, "financialEvents", 4));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> accounts(boolean includedOnly) {
        Long userId = currentUser().getId();
        List<Account> rows = accounts.findByUserId(userId).stream()
                .filter(a -> !includedOnly || a.isIncludedInAssets())
                .map(a -> new Account(a.getId(), a.getBankName(), a.getAccountName(), a.getBalance(), a.getPurpose(), a.isIncludedInAssets()))
                .toList();
        return Map.of("totalBalance", rows.stream().mapToLong(Account::balance).sum(), "accounts", rows);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> cards(Boolean active) {
        Long userId = currentUser().getId();
        return Map.of("cards", cards.findByUserId(userId).stream()
                .filter(c -> active == null || c.isActive() == active)
                .map(c -> new Card(c.getId(), c.getCardCompany(), c.getCardName(), c.getPaymentDay(), c.isActive()))
                .toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> categories(CategoryType categoryType) {
        Long userId = currentUser().getId();
        List<CategoryEntity> rows = categoryType == null ? categories.findByUserId(userId) : categories.findByUserIdAndCategoryType(userId, categoryType);
        return Map.of("categories", rows.stream().map(this::toCategory).toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> transactions(YearMonth month, TransactionType type, Long categoryId, String keyword, int page, int size) {
        Long userId = currentUser().getId();
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        Map<Long, CategoryEntity> categoryMap = categories.findByUserId(userId).stream().collect(Collectors.toMap(CategoryEntity::getId, Function.identity()));
        List<TransactionView> filtered = transactions.findByUserIdAndTransactionDateBetween(userId, from, to).stream()
                .filter(t -> type == null || t.getTransactionType() == type)
                .filter(t -> categoryId == null || Objects.equals(t.getCategoryId(), categoryId))
                .filter(t -> keyword == null || t.getMerchant().contains(keyword))
                .map(t -> toTransactionView(t, categoryMap))
                .toList();
        int start = Math.min(page * size, filtered.size());
        int end = Math.min(start + size, filtered.size());
        return Map.of("month", month.toString(), "totalAmount", filtered.stream().mapToLong(TransactionView::amount).sum(), "items", filtered.subList(start, end), "page", page, "size", size, "totalElements", filtered.size());
    }

    public Map<String, Object> updateTransactionCategory(long transactionId, long categoryId) {
        Long userId = currentUser().getId();
        TransactionEntity tx = transactions.findById(transactionId).orElseThrow(() -> notFound("Transaction not found."));
        assertOwner(tx.getUserId(), userId);
        CategoryEntity category = categories.findById(categoryId).orElseThrow(() -> notFound("Category not found."));
        assertOwner(category.getUserId(), userId);
        tx.changeCategory(categoryId);
        return Map.of("transactionId", transactionId, "categoryId", categoryId, "categoryName", category.getName(), "updatedAt", LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> financialEvents(LocalDate from, LocalDate to, Direction direction, EventType eventType, EventStatus status) {
        forecastService.validateRange(from, to);
        Long userId = currentUser().getId();
        return Map.of("from", from, "to", to, "events", events.findByUserIdAndEventDateBetween(userId, from, to).stream()
                .filter(e -> direction == null || e.getDirection() == direction)
                .filter(e -> eventType == null || e.getEventType() == eventType)
                .filter(e -> status == null || e.getStatus() == status)
                .map(this::toFinancialEvent)
                .toList());
    }

    public FinancialEvent createEvent(EventMutation request) {
        Long userId = currentUser().getId();
        return toFinancialEvent(events.save(new FinancialEventEntity(userId, request.eventDate(), request.title(), request.amount(), request.direction(), request.eventType(), EventStatus.SCHEDULED, request.fixed())));
    }

    public Map<String, Object> updateEvent(long eventId, EventPatch request) {
        Long userId = currentUser().getId();
        FinancialEventEntity event = events.findById(eventId).orElseThrow(() -> notFound("Financial event not found."));
        assertOwner(event.getUserId(), userId);
        event.update(request.eventDate(), request.title(), request.amount(), request.status(), request.fixed());
        return Map.of("eventId", event.getId(), "eventDate", event.getEventDate(), "amount", event.getAmount(), "status", event.getStatus(), "updatedAt", LocalDateTime.now());
    }

    public Map<String, Object> cancelEvent(long eventId) {
        Long userId = currentUser().getId();
        FinancialEventEntity event = events.findById(eventId).orElseThrow(() -> notFound("Financial event not found."));
        assertOwner(event.getUserId(), userId);
        if (event.getStatus() == EventStatus.CANCELED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Event is already canceled.");
        }
        event.cancel();
        return Map.of("eventId", eventId, "status", EventStatus.CANCELED, "canceledAt", LocalDateTime.now());
    }

    public RecurringRule createRule(RecurringMutation request) {
        Long userId = currentUser().getId();
        RecurringRuleEntity rule = rules.save(new RecurringRuleEntity(userId, request.title(), request.recurrenceType(), request.dayOfMonth(), request.startDate(), request.endDate(), request.amount(), request.eventType(), request.direction(), true));
        generateEventFromRule(userId, rule);
        return toRecurringRule(rule);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> rules() {
        Long userId = currentUser().getId();
        return Map.of("recurringRules", rules.findByUserId(userId).stream().map(this::toRecurringRule).toList());
    }

    public RecurringRule updateRule(long id, RecurringPatch request) {
        Long userId = currentUser().getId();
        RecurringRuleEntity rule = rules.findById(id).orElseThrow(() -> notFound("Recurring rule not found."));
        assertOwner(rule.getUserId(), userId);
        rule.update(request.title(), request.recurrenceType(), request.dayOfMonth(), request.startDate(), request.endDate(), request.amount(), request.eventType(), request.direction(), request.active());
        return toRecurringRule(rule);
    }

    public Map<String, Object> deleteRule(long id) {
        Long userId = currentUser().getId();
        RecurringRuleEntity rule = rules.findById(id).orElseThrow(() -> notFound("Recurring rule not found."));
        assertOwner(rule.getUserId(), userId);
        rule.update(null, null, null, null, null, null, null, null, false);
        return Map.of("recurringRuleId", id, "active", false, "deletedAt", LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> forecast(LocalDate from, LocalDate to) {
        Long userId = currentUser().getId();
        long currentBalance = forecastService.currentBalance(accounts.findByUserId(userId));
        return forecastService.forecast(from, to, currentBalance, events.findByUserIdAndEventDateBetween(userId, from, to));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(LocalDate baseDate, LocalDate targetDate) {
        UserEntity user = currentUser();
        LocalDate base = baseDate == null ? YearMonth.parse(user.getBaseMonth()).atDay(1) : baseDate;
        LocalDate target = targetDate == null ? base.withDayOfMonth(base.lengthOfMonth()) : targetDate;
        List<AccountEntity> accountRows = accounts.findByUserId(user.getId());
        List<FinancialEventEntity> eventRows = events.findByUserId(user.getId());
        long currentBalance = forecastService.currentBalance(accountRows);
        long fixedOutflows = forecastService.fixedOutflows(eventRows);
        long available = currentBalance - fixedOutflows;
        return Map.of("currentBalance", currentBalance, "fixedOutflows", fixedOutflows, "availableAmount", available, "weather", forecastService.weather(available), "riskSummary", forecastService.risk(available), "nextEvents", upcoming(eventRows, base, target));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> simulate(SimulationRequest request) {
        long beforeAmount = ((Number) dashboard(LocalDate.now(), request.targetDate()).get("availableAmount")).longValue();
        long afterAmount = beforeAmount - request.amount();
        return Map.of("before", Map.of("availableAmount", beforeAmount, "weather", forecastService.weather(beforeAmount)), "after", Map.of("availableAmount", afterAmount, "weather", forecastService.weather(afterAmount)), "difference", -request.amount(), "message", request.amount() + "원을 추가로 사용하면 날씨가 " + forecastService.weather(beforeAmount) + "에서 " + forecastService.weather(afterAmount) + "로 바뀔 수 있습니다.");
    }

    public Map<String, Object> startConversation(String title) {
        Long userId = currentUser().getId();
        ConversationEntity conversation = conversations.save(new ConversationEntity(userId, title == null ? "새 대화" : title, LocalDateTime.now()));
        return Map.of("conversationId", conversation.getId(), "title", conversation.getTitle(), "createdAt", conversation.getCreatedAt());
    }

    public Map<String, Object> chat(ChatRequest request) {
        Long userId = currentUser().getId();
        ConversationEntity conversation = request.conversationId() == null
                ? conversations.save(new ConversationEntity(userId, "AI 상담", LocalDateTime.now()))
                : conversations.findById(request.conversationId()).orElseThrow(() -> notFound("Conversation not found."));
        assertOwner(conversation.getUserId(), userId);

        messages.save(new MessageEntity(conversation.getId(), "USER", request.message(), "", LocalDateTime.now()));
        Map<String, Object> toolResult = agentToolSnapshot();
        AgentAnswerClient.AgentAnswer answer = agentAnswerClient.answer(request.message(), toolResult);
        MessageEntity assistant = messages.save(new MessageEntity(conversation.getId(), "ASSISTANT", answer.content(), "dashboard,financial-events,forecasts", LocalDateTime.now()));
        return Map.of("conversationId", conversation.getId(), "messageId", assistant.getId(), "answer", answer.content(), "provider", answer.provider(), "sources", List.of("dashboard", "financial-events", "forecasts"), "toolCalls", toolResult.get("toolCalls"));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> messages(long id) {
        ConversationEntity conversation = conversations.findById(id).orElseThrow(() -> notFound("Conversation not found."));
        assertOwner(conversation.getUserId(), currentUser().getId());
        return Map.of("conversationId", id, "messages", messages.findByConversationIdOrderByCreatedAtAsc(id).stream().map(this::toMessage).toList());
    }

    @Transactional(readOnly = true)
    public MonthlyBudget getBudget() {
        Long userId = currentUser().getId();
        MonthlyBudgetEntity budget = budgets.findFirstByUserIdOrderByBudgetMonthDesc(userId).orElseThrow(() -> notFound("Monthly budget not found."));
        return toMonthlyBudget(budget);
    }

    public MonthlyBudget putBudget(MonthlyBudget budget) {
        Long userId = currentUser().getId();
        MonthlyBudgetEntity entity = budgets.findByUserIdAndBudgetMonth(userId, budget.month().toString())
                .orElseGet(() -> budgets.save(new MonthlyBudgetEntity(userId, budget.month().toString(), budget.totalLimit())));
        entity.update(budget.month().toString(), budget.totalLimit());
        budgetLimits.deleteByBudgetId(entity.getId());
        budgetLimits.saveAll(budget.categoryLimits().entrySet().stream()
                .map(entry -> new BudgetCategoryLimitEntity(entity.getId(), entry.getKey(), entry.getValue()))
                .toList());
        return toMonthlyBudget(entity);
    }

    public Map<String, Object> weatherStatuses() {
        return Map.of("statuses", List.of(
                Map.of("weather", WeatherStatus.SUNNY, "label", "맑음", "description", "여유 자금이 충분합니다."),
                Map.of("weather", WeatherStatus.CLOUDY, "label", "흐림", "description", "예정 지출을 확인하세요."),
                Map.of("weather", WeatherStatus.RAINY, "label", "비", "description", "추가 소비를 줄이는 것이 좋습니다."),
                Map.of("weather", WeatherStatus.STORM, "label", "폭풍", "description", "생활비 부족 위험이 큽니다.")
        ));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> forecastAlerts() {
        Map<String, Object> dashboard = dashboard(null, null);
        return Map.of("alerts", List.of(dashboard.get("riskSummary")));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> agentSuggestions() {
        return Map.of("suggestions", List.of(
                "이번 달 자금 날씨가 왜 이렇게 나왔어?",
                "다음 카드 결제 전까지 얼마를 써도 돼?",
                "반복 지출 중 줄일 만한 게 있어?",
                "이번 달 위험한 날짜를 알려줘"
        ));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> analyzeSpending() {
        Long userId = currentUser().getId();
        YearMonth month = YearMonth.parse(currentUser().getBaseMonth());
        Map<Long, CategoryEntity> categoryMap = categories.findByUserId(userId).stream().collect(Collectors.toMap(CategoryEntity::getId, Function.identity()));
        Map<String, Long> byCategory = transactions.findByUserIdAndTransactionDateBetween(userId, month.atDay(1), month.atEndOfMonth()).stream()
                .filter(t -> t.getTransactionType() == TransactionType.EXPENSE)
                .collect(Collectors.groupingBy(t -> Optional.ofNullable(categoryMap.get(t.getCategoryId())).map(CategoryEntity::getName).orElse("미분류"), Collectors.summingLong(TransactionEntity::getAmount)));
        return Map.of("month", month.toString(), "totalExpense", byCategory.values().stream().mapToLong(Long::longValue).sum(), "byCategory", byCategory);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detectRecurring() {
        Long userId = currentUser().getId();
        List<Map<String, Object>> candidates = rules.findByUserId(userId).stream()
                .filter(RecurringRuleEntity::isActive)
                .map(rule -> Map.<String, Object>of("title", rule.getTitle(), "amount", rule.getAmount(), "recurrenceType", rule.getRecurrenceType(), "confidence", 0.95))
                .toList();
        return Map.of("candidates", candidates);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> recommendActions() {
        Map<String, Object> dashboard = dashboard(null, null);
        WeatherStatus weather = (WeatherStatus) dashboard.get("weather");
        List<String> actions = switch (weather) {
            case SUNNY -> List.of("현재 소비 계획을 유지하세요.", "남는 금액은 예비비로 분리해도 좋습니다.");
            case CLOUDY -> List.of("다음 예정 지출 전 카테고리별 예산을 확인하세요.", "추가 소비는 시뮬레이션 후 결정하세요.");
            case RAINY -> List.of("이번 주 비필수 지출을 줄이세요.", "반복 구독 지출을 점검하세요.");
            case STORM -> List.of("즉시 예정 지출과 잔액을 확인하세요.", "고정 지출 일정 조정이 가능한지 확인하세요.");
        };
        return Map.of("weather", weather, "actions", actions, "riskSummary", dashboard.get("riskSummary"));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> evidence(long messageId) {
        MessageEntity message = messages.findById(messageId).orElseThrow(() -> notFound("Message not found."));
        ConversationEntity conversation = conversations.findById(message.getConversationId()).orElseThrow(() -> notFound("Conversation not found."));
        assertOwner(conversation.getUserId(), currentUser().getId());
        return Map.of("messageId", messageId, "sources", splitSources(message.getSources()), "snapshot", agentToolSnapshot());
    }

    private Map<String, Object> agentToolSnapshot() {
        LocalDate from = YearMonth.parse(currentUser().getBaseMonth()).atDay(1);
        LocalDate to = YearMonth.from(from).atEndOfMonth();
        return Map.of(
                "toolCalls", List.of("dashboard", "financial-events", "forecasts"),
                "dashboard", dashboard(from, to),
                "financialEvents", financialEvents(from, to, null, null, null).get("events"),
                "forecast", forecast(from, to)
        );
    }

    private void generateEventFromRule(Long userId, RecurringRuleEntity rule) {
        if (rule.getRecurrenceType() != RecurrenceType.MONTHLY || rule.getDayOfMonth() == null) return;
        YearMonth month = YearMonth.from(rule.getStartDate());
        LocalDate eventDate = month.atDay(Math.min(rule.getDayOfMonth(), month.lengthOfMonth()));
        events.save(new FinancialEventEntity(userId, eventDate, rule.getTitle(), rule.getAmount(), rule.getDirection(), rule.getEventType(), EventStatus.SCHEDULED, true));
    }

    private void clearAll() {
        messages.deleteAll();
        conversations.deleteAll();
        budgetLimits.deleteAll();
        budgets.deleteAll();
        rules.deleteAll();
        events.deleteAll();
        transactions.deleteAll();
        categories.deleteAll();
        cards.deleteAll();
        accounts.deleteAll();
        users.deleteAll();
    }

    private UserEntity currentUser() {
        return requireUser(UserContext.currentUserId());
    }

    private UserEntity requireUser(Long userId) {
        Long id = userId == null ? DEFAULT_USER_ID : userId;
        UserEntity user = users.findById(id).orElseThrow(() -> notFound("User not found."));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is inactive.");
        }
        return user;
    }

    private void assertOwner(Long ownerUserId, Long currentUserId) {
        if (!Objects.equals(ownerUserId, currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Resource owner does not match current user.");
        }
    }

    private List<Map<String, Object>> upcoming(List<FinancialEventEntity> eventRows, LocalDate from, LocalDate to) {
        return eventRows.stream()
                .filter(e -> !e.getEventDate().isBefore(from) && !e.getEventDate().isAfter(to) && e.getStatus() != EventStatus.CANCELED)
                .sorted(Comparator.comparing(FinancialEventEntity::getEventDate))
                .limit(5)
                .map(e -> Map.<String, Object>of("date", e.getEventDate(), "title", e.getTitle(), "amount", e.getAmount(), "direction", e.getDirection()))
                .toList();
    }

    private Category toCategory(CategoryEntity category) {
        return new Category(category.getId(), category.getName(), category.getCategoryType(), category.isSystemDefault());
    }

    private TransactionView toTransactionView(TransactionEntity tx, Map<Long, CategoryEntity> categoryMap) {
        String categoryName = Optional.ofNullable(categoryMap.get(tx.getCategoryId())).map(CategoryEntity::getName).orElse("미분류");
        return new TransactionView(tx.getId(), tx.getTransactionDate(), tx.getMerchant(), tx.getAmount(), tx.getTransactionType(), categoryName);
    }

    private FinancialEvent toFinancialEvent(FinancialEventEntity event) {
        return new FinancialEvent(event.getId(), event.getEventDate(), event.getTitle(), event.getAmount(), event.getDirection(), event.getEventType(), event.getStatus(), event.isFixed());
    }

    private RecurringRule toRecurringRule(RecurringRuleEntity rule) {
        return new RecurringRule(rule.getId(), rule.getTitle(), rule.getRecurrenceType(), rule.getDayOfMonth(), rule.getStartDate(), rule.getEndDate(), rule.getAmount(), rule.getEventType(), rule.getDirection(), rule.isActive());
    }

    private MonthlyBudget toMonthlyBudget(MonthlyBudgetEntity budget) {
        Map<String, Integer> limits = budgetLimits.findByBudgetId(budget.getId()).stream()
                .collect(Collectors.toMap(BudgetCategoryLimitEntity::getCategoryName, BudgetCategoryLimitEntity::getLimitAmount));
        return new MonthlyBudget(YearMonth.parse(budget.getBudgetMonth()), limits, budget.getTotalLimitAmount());
    }

    private Message toMessage(MessageEntity message) {
        return new Message(message.getId(), message.getRole(), message.getContent(), splitSources(message.getSources()), message.getCreatedAt());
    }

    private List<String> splitSources(String sources) {
        if (sources == null || sources.isBlank()) return List.of();
        return Arrays.stream(sources.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    public record User(Long userId, String name, YearMonth baseMonth, UserStatus status) {}
    public record Account(Long accountId, String bankName, String accountName, long balance, String purpose, boolean includedInAssets) {}
    public record Card(Long cardId, String cardCompany, String cardName, int paymentDay, boolean active) {}
    public record Category(Long categoryId, String name, CategoryType categoryType, boolean systemDefault) {}
    public record TransactionView(Long transactionId, LocalDate date, String merchant, long amount, TransactionType transactionType, String category) {}
    public record FinancialEvent(Long eventId, LocalDate eventDate, String title, long amount, Direction direction, EventType eventType, EventStatus status, boolean fixed) {}
    public record RecurringRule(Long recurringRuleId, String title, RecurrenceType recurrenceType, Integer dayOfMonth, LocalDate startDate, LocalDate endDate, long amount, EventType eventType, Direction direction, boolean active) {}
    public record MonthlyBudget(YearMonth month, Map<String, Integer> categoryLimits, int totalLimit) {}
    public record Message(Long messageId, String role, String content, List<String> sources, LocalDateTime createdAt) {}
    public record EventMutation(LocalDate eventDate, String title, long amount, Direction direction, EventType eventType, boolean fixed) {}
    public record EventPatch(LocalDate eventDate, String title, Long amount, EventStatus status, Boolean fixed) {}
    public record RecurringMutation(String title, RecurrenceType recurrenceType, Integer dayOfMonth, LocalDate startDate, LocalDate endDate, long amount, EventType eventType, Direction direction) {}
    public record RecurringPatch(String title, RecurrenceType recurrenceType, Integer dayOfMonth, LocalDate startDate, LocalDate endDate, Long amount, EventType eventType, Direction direction, Boolean active) {}
    public record SimulationRequest(LocalDate spendingDate, long amount, LocalDate targetDate, String title) {}
    public record ChatRequest(Long conversationId, String message) {}
}
