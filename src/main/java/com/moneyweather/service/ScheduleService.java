package com.moneyweather.service;

import com.moneyweather.domain.Enums.*;
import com.moneyweather.domain.entity.CardEntity;
import com.moneyweather.domain.entity.FinancialEventEntity;
import com.moneyweather.domain.entity.RecurringRuleEntity;
import com.moneyweather.domain.entity.TransactionEntity;
import com.moneyweather.repository.CardRepository;
import com.moneyweather.repository.FinancialEventRepository;
import com.moneyweather.repository.RecurringRuleRepository;
import com.moneyweather.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 앞으로의 현금 흐름: 예정 이벤트와 반복 규칙.
 *
 * <p>반복 규칙과 카드 결제일에서 나오는 이벤트는 미리 만들지 않고, 그 달을 조회할 때 채운다
 * ({@link #materializeRange}). 그래서 이벤트를 읽는 조회 메서드도 쓰기 트랜잭션으로 돈다.
 */
@Service
@Transactional
public class ScheduleService {
    /** 한 번의 조회로 펼칠 수 있는 최대 개월 수. 과도한 기간 요청으로 이벤트가 폭증하는 것을 막는다. */
    private static final int MAX_MATERIALIZED_MONTHS = 24;

    private final FinancialEventRepository events;
    private final RecurringRuleRepository rules;
    private final CardRepository cards;
    private final TransactionRepository transactions;
    private final LedgerService ledger;
    private final CurrentUserService currentUser;
    private final ForecastService forecastService;

    public ScheduleService(FinancialEventRepository events, RecurringRuleRepository rules, CardRepository cards,
                           TransactionRepository transactions, LedgerService ledger, CurrentUserService currentUser,
                           ForecastService forecastService) {
        this.events = events;
        this.rules = rules;
        this.cards = cards;
        this.transactions = transactions;
        this.ledger = ledger;
        this.currentUser = currentUser;
        this.forecastService = forecastService;
    }

    public record FinancialEvent(Long eventId, LocalDate eventDate, String title, long amount, Direction direction, EventType eventType,
                                 EventStatus status, boolean fixed, Long accountId, Long recurringRuleId, Long cardId) {}
    public record EventMutation(LocalDate eventDate, String title, long amount, Direction direction, EventType eventType, boolean fixed, Long accountId) {}
    public record EventPatch(LocalDate eventDate, String title, Long amount, EventStatus status, Boolean fixed, Long accountId) {}
    public record RecurringRule(Long recurringRuleId, String title, RecurrenceType recurrenceType, Integer dayOfMonth, LocalDate startDate, LocalDate endDate,
                                long amount, EventType eventType, Direction direction, boolean active, Long accountId) {}
    public record RuleCreation(RecurringRule rule, int generatedEvents) {}
    public record RecurringMutation(String title, RecurrenceType recurrenceType, Integer dayOfMonth, LocalDate startDate, LocalDate endDate,
                                    long amount, EventType eventType, Direction direction, Long accountId) {}
    public record RecurringPatch(String title, RecurrenceType recurrenceType, Integer dayOfMonth, LocalDate startDate, LocalDate endDate,
                                 Long amount, EventType eventType, Direction direction, Boolean active, Long accountId) {}

    // --- 예정 이벤트 --------------------------------------------------------

    public Map<String, Object> events(LocalDate from, LocalDate to, Direction direction, EventType eventType, EventStatus status) {
        forecastService.validateRange(from, to);
        Long userId = currentUser.requireId();
        materializeRange(userId, from, to);
        return Map.of("from", from, "to", to, "events", events.findByUserIdAndEventDateBetween(userId, from, to).stream()
                .filter(e -> direction == null || e.getDirection() == direction)
                .filter(e -> eventType == null || e.getEventType() == eventType)
                .filter(e -> status == null || e.getStatus() == status)
                .map(this::toEvent)
                .toList());
    }

    public FinancialEvent createEvent(EventMutation request) {
        Long userId = currentUser.requireId();
        if (request.accountId() != null) ledger.requireOwnedAccount(userId, request.accountId());
        return toEvent(events.save(new FinancialEventEntity(userId, request.eventDate(), request.title(), request.amount(),
                request.direction(), request.eventType(), EventStatus.SCHEDULED, request.fixed(), null, null, request.accountId())));
    }

    /**
     * 이벤트 수정. {@code status=PAID}로 바꾸면 연결 계좌 잔액에 반영되고, PAID 에서 벗어나면 되돌린다.
     * 이미 결제된 이벤트의 금액을 바꿔도 잔액이 함께 맞춰진다.
     */
    public Map<String, Object> updateEvent(long eventId, EventPatch request) {
        Long userId = currentUser.requireId();
        FinancialEventEntity event = requireOwnedEvent(userId, eventId);
        if (request.accountId() != null) ledger.requireOwnedAccount(userId, request.accountId());

        ledger.revert(event);
        event.update(request.eventDate(), request.title(), request.amount(), request.fixed(), request.accountId());
        if (request.status() != null) event.changeStatus(request.status());
        ledger.apply(event);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", event.getId());
        body.put("eventDate", event.getEventDate());
        body.put("amount", event.getAmount());
        body.put("status", event.getStatus());
        body.put("accountId", event.getAccountId());
        body.put("updatedAt", LocalDateTime.now());
        return body;
    }

    public Map<String, Object> cancelEvent(long eventId) {
        FinancialEventEntity event = requireOwnedEvent(currentUser.requireId(), eventId);
        if (event.getStatus() == EventStatus.CANCELED) {
            throw Errors.conflict("Event is already canceled.");
        }
        ledger.revert(event);   // 이미 결제된 이벤트를 취소하면 잔액을 되돌린다
        event.cancel();
        return Map.of("eventId", eventId, "status", EventStatus.CANCELED, "canceledAt", LocalDateTime.now());
    }

    private FinancialEventEntity requireOwnedEvent(Long userId, long eventId) {
        FinancialEventEntity event = events.findById(eventId).orElseThrow(() -> Errors.notFound("Financial event not found."));
        currentUser.assertOwner(event.getUserId(), userId);
        return event;
    }

    // --- 반복 규칙 ----------------------------------------------------------

    /** 규칙을 만들고 시작 월분을 즉시 펼친다. 나머지 달은 조회 시점에 채워진다. */
    public RuleCreation createRule(RecurringMutation request) {
        Long userId = currentUser.requireId();
        if (request.accountId() != null) ledger.requireOwnedAccount(userId, request.accountId());
        validateRecurringMutation(request);
        RecurringRuleEntity rule = rules.save(new RecurringRuleEntity(userId, request.title(), request.recurrenceType(), request.dayOfMonth(),
                request.startDate(), request.endDate(), request.amount(), request.eventType(), request.direction(), true, request.accountId()));
        rules.flush();
        YearMonth startMonth = YearMonth.from(rule.getStartDate());
        int generated = materializeRange(userId, startMonth.atDay(1), startMonth.atEndOfMonth());
        return new RuleCreation(toRule(rule), generated);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> rules() {
        Long userId = currentUser.requireId();
        return Map.of("recurringRules", rules.findByUserId(userId).stream().map(this::toRule).toList());
    }

    public RecurringRule updateRule(long id, RecurringPatch request) {
        Long userId = currentUser.requireId();
        RecurringRuleEntity rule = requireOwnedRule(userId, id);
        if (request.accountId() != null) ledger.requireOwnedAccount(userId, request.accountId());
        validateRecurringPatch(rule, request);
        rule.update(request.title(), request.recurrenceType(), request.dayOfMonth(), request.startDate(), request.endDate(), request.amount(), request.eventType(), request.direction(), request.active());
        rule.changeAccount(request.accountId());
        rules.flush();
        dropFutureGeneratedEvents(rule.getId());
        if (rule.isActive()) {
            YearMonth thisMonth = YearMonth.from(LocalDate.now());
            materializeRange(userId, thisMonth.atDay(1), thisMonth.plusMonths(2).atEndOfMonth());
        }
        return toRule(rule);
    }

    public Map<String, Object> deleteRule(long id) {
        RecurringRuleEntity rule = requireOwnedRule(currentUser.requireId(), id);
        rule.update(null, null, null, null, null, null, null, null, false);
        rules.flush();
        dropFutureGeneratedEvents(rule.getId());
        return Map.of("recurringRuleId", id, "active", false, "deletedAt", LocalDateTime.now());
    }

    private RecurringRuleEntity requireOwnedRule(Long userId, long id) {
        RecurringRuleEntity rule = rules.findById(id).orElseThrow(() -> Errors.notFound("Recurring rule not found."));
        currentUser.assertOwner(rule.getUserId(), userId);
        return rule;
    }

    private void validateRecurringMutation(RecurringMutation request) {
        validateRecurringValues(request.recurrenceType(), request.dayOfMonth(), request.startDate(), request.endDate());
    }

    private void validateRecurringPatch(RecurringRuleEntity current, RecurringPatch request) {
        RecurrenceType effectiveType = request.recurrenceType() == null ? current.getRecurrenceType() : request.recurrenceType();
        Integer effectiveDay = request.dayOfMonth() == null ? current.getDayOfMonth() : request.dayOfMonth();
        LocalDate effectiveStart = request.startDate() == null ? current.getStartDate() : request.startDate();
        LocalDate effectiveEnd = request.endDate() == null ? current.getEndDate() : request.endDate();
        validateRecurringValues(effectiveType, effectiveDay, effectiveStart, effectiveEnd);
    }

    private void validateRecurringValues(RecurrenceType recurrenceType, Integer dayOfMonth, LocalDate startDate, LocalDate endDate) {
        if (recurrenceType == RecurrenceType.MONTHLY && dayOfMonth == null) {
            throw Errors.badRequest("MONTHLY recurring rule requires dayOfMonth.");
        }
        if (endDate != null && endDate.isBefore(startDate)) {
            throw Errors.badRequest("endDate must be same as or after startDate.");
        }
    }

    /**
     * 규칙이 만들어 둔 이벤트 중 아직 지나지 않은 예정분만 지운다.
     * 이미 지난 이벤트는 실제 있었던 일이므로 소급해서 바꾸지 않는다.
     */
    private void dropFutureGeneratedEvents(Long ruleId) {
        events.deleteByRecurringRuleIdAndEventDateGreaterThanEqualAndStatus(ruleId, LocalDate.now(), EventStatus.SCHEDULED);
        events.flush();
    }

    // --- 이벤트 자동 생성 ------------------------------------------------------

    /**
     * 조회 대상 기간에 걸친 각 달에 대해, 반복 규칙과 카드 결제일에서 파생되는 예정 이벤트를 채워 넣는다.
     * 이미 만들어 둔 이벤트는 다시 만들지 않고 금액/제목만 원본에 맞춰 갱신한다.
     * 생성한 이벤트 수를 돌려준다.
     */
    public int materializeRange(Long userId, LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) return 0;
        List<RecurringRuleEntity> activeRules = rules.findByUserId(userId).stream().filter(RecurringRuleEntity::isActive).toList();
        List<CardEntity> activeCards = cards.findByUserId(userId).stream().filter(CardEntity::isActive).toList();

        int created = 0;
        YearMonth last = YearMonth.from(to);
        YearMonth month = YearMonth.from(from);
        for (int guard = 0; !month.isAfter(last) && guard < MAX_MATERIALIZED_MONTHS; guard++, month = month.plusMonths(1)) {
            for (RecurringRuleEntity rule : activeRules) {
                created += materializeRule(userId, rule, month);
            }
            for (CardEntity card : activeCards) {
                created += materializeCardBill(userId, card, month);
            }
        }
        return created;
    }

    private int materializeRule(Long userId, RecurringRuleEntity rule, YearMonth month) {
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();
        if (rule.getStartDate().isAfter(monthEnd)) return 0;
        if (rule.getEndDate() != null && rule.getEndDate().isBefore(monthStart)) return 0;

        Map<LocalDate, FinancialEventEntity> existing = events.findByRecurringRuleIdAndEventDateBetween(rule.getId(), monthStart, monthEnd).stream()
                .collect(Collectors.toMap(FinancialEventEntity::getEventDate, Function.identity(), (a, b) -> a));

        int created = 0;
        for (LocalDate date : occurrences(rule, month)) {
            if (date.isBefore(rule.getStartDate())) continue;
            if (rule.getEndDate() != null && date.isAfter(rule.getEndDate())) continue;
            FinancialEventEntity already = existing.get(date);
            if (already != null) {
                if (already.getStatus() == EventStatus.SCHEDULED && !isPast(date)) {
                    already.syncGenerated(rule.getTitle(), rule.getAmount(), rule.getAccountId());
                }
                continue;
            }
            events.save(new FinancialEventEntity(userId, date, rule.getTitle(), rule.getAmount(),
                    rule.getDirection(), rule.getEventType(), EventStatus.SCHEDULED, true, rule.getId(), null, rule.getAccountId()));
            created++;
        }
        return created;
    }

    /** 규칙이 해당 월에 발생시키는 날짜들. */
    private List<LocalDate> occurrences(RecurringRuleEntity rule, YearMonth month) {
        return switch (rule.getRecurrenceType()) {
            case MONTHLY -> rule.getDayOfMonth() == null
                    ? List.of()
                    : List.of(month.atDay(Math.min(rule.getDayOfMonth(), month.lengthOfMonth())));
            case WEEKLY -> {
                List<LocalDate> dates = new ArrayList<>();
                LocalDate monthStart = month.atDay(1);
                LocalDate cursor = rule.getStartDate();
                if (cursor.isBefore(monthStart)) {
                    long weeks = (monthStart.toEpochDay() - cursor.toEpochDay() + 6) / 7;
                    cursor = cursor.plusWeeks(weeks);
                }
                for (LocalDate d = cursor; !d.isAfter(month.atEndOfMonth()); d = d.plusWeeks(1)) {
                    dates.add(d);
                }
                yield dates;
            }
            case YEARLY -> rule.getStartDate().getMonthValue() != month.getMonthValue()
                    ? List.of()
                    : List.of(month.atDay(Math.min(rule.getStartDate().getDayOfMonth(), month.lengthOfMonth())));
        };
    }

    /** 전월 카드 사용분을 합산해 당월 결제일에 청구 이벤트를 만든다. 사용액이 없으면 만들지 않는다. */
    private int materializeCardBill(Long userId, CardEntity card, YearMonth month) {
        YearMonth usageMonth = month.minusMonths(1);
        long billed = transactions.findByUserIdAndCardIdAndTransactionDateBetween(userId, card.getId(), usageMonth.atDay(1), usageMonth.atEndOfMonth()).stream()
                .filter(t -> t.getTransactionType() == TransactionType.EXPENSE)
                .mapToLong(TransactionEntity::getAmount)
                .sum();
        LocalDate paymentDate = month.atDay(Math.min(card.getPaymentDay(), month.lengthOfMonth()));
        Optional<FinancialEventEntity> existing = events.findByCardIdAndEventDate(card.getId(), paymentDate);
        String title = card.getCardName() + " 결제";

        if (existing.isPresent()) {
            FinancialEventEntity bill = existing.get();
            if (bill.getStatus() == EventStatus.SCHEDULED && !isPast(paymentDate)) {
                bill.syncGenerated(title, billed, card.getPaymentAccountId());
            }
            return 0;
        }
        if (billed <= 0) return 0;
        events.save(new FinancialEventEntity(userId, paymentDate, title, billed,
                Direction.OUTFLOW, EventType.CARD_BILL, EventStatus.SCHEDULED, true, null, card.getId(), card.getPaymentAccountId()));
        return 1;
    }

    /** 이미 지난 이벤트는 실제로 있었던 일이므로 원본이 바뀌어도 소급 수정하지 않는다. */
    private static boolean isPast(LocalDate date) {
        return date.isBefore(LocalDate.now());
    }

    private FinancialEvent toEvent(FinancialEventEntity event) {
        return new FinancialEvent(event.getId(), event.getEventDate(), event.getTitle(), event.getAmount(), event.getDirection(), event.getEventType(),
                event.getStatus(), event.isFixed(), event.getAccountId(), event.getRecurringRuleId(), event.getCardId());
    }

    private RecurringRule toRule(RecurringRuleEntity rule) {
        return new RecurringRule(rule.getId(), rule.getTitle(), rule.getRecurrenceType(), rule.getDayOfMonth(), rule.getStartDate(), rule.getEndDate(),
                rule.getAmount(), rule.getEventType(), rule.getDirection(), rule.isActive(), rule.getAccountId());
    }
}
