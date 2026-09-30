package com.moneyweather.service;

import com.moneyweather.domain.Enums.Direction;
import com.moneyweather.domain.Enums.EventType;
import com.moneyweather.domain.Enums.RecurrenceType;
import com.moneyweather.domain.Enums.TransactionType;
import com.moneyweather.domain.entity.CategoryEntity;
import com.moneyweather.domain.entity.RecurringRuleEntity;
import com.moneyweather.domain.entity.TransactionEntity;
import com.moneyweather.domain.entity.UserEntity;
import com.moneyweather.repository.CategoryRepository;
import com.moneyweather.repository.RecurringRuleRepository;
import com.moneyweather.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 거래 내역에서 매달 반복되는 지출을 찾아, 아직 반복 규칙으로 등록되지 않은 것을 후보로 돌려준다.
 *
 * <p>같은 상호(공백·대소문자 무시)로 최근 {@value #LOOKBACK_MONTHS}개월 중 {@value #MIN_MONTHS}개월 이상 지출했고,
 * 금액 변동이 작으면(변동계수 ≤ {@value #MAX_AMOUNT_CV}) 반복 지출로 본다.
 */
@Service
@Transactional(readOnly = true)
public class RecurringDetectionService {
    static final int LOOKBACK_MONTHS = 6;
    static final int MIN_MONTHS = 3;
    static final double MAX_AMOUNT_CV = 0.2;

    private final CurrentUserService currentUser;
    private final TransactionRepository transactions;
    private final RecurringRuleRepository rules;
    private final CategoryRepository categories;

    public RecurringDetectionService(CurrentUserService currentUser, TransactionRepository transactions,
                                     RecurringRuleRepository rules, CategoryRepository categories) {
        this.currentUser = currentUser;
        this.transactions = transactions;
        this.rules = rules;
        this.categories = categories;
    }

    public record SuggestedRule(String title, RecurrenceType recurrenceType, int dayOfMonth, LocalDate startDate,
                                long amount, EventType eventType, Direction direction, Long accountId) {}

    public record Candidate(String merchant, int monthsObserved, List<String> months, long typicalAmount,
                            int typicalDayOfMonth, double confidence, String reason, SuggestedRule suggestedRule) {}

    /** 사용자의 기준 월까지 최근 6개월을 본다. */
    public List<Candidate> detect() {
        UserEntity user = currentUser.require();
        YearMonth endMonth = YearMonth.parse(user.getBaseMonth());
        YearMonth startMonth = endMonth.minusMonths(LOOKBACK_MONTHS - 1);
        List<TransactionEntity> expenses = transactions.findByUserIdAndTransactionDateBetween(user.getId(), startMonth.atDay(1), endMonth.atEndOfMonth())
                .stream().filter(t -> t.getTransactionType() == TransactionType.EXPENSE).toList();

        Set<String> registered = rules.findByUserId(user.getId()).stream()
                .filter(RecurringRuleEntity::isActive)
                .map(r -> normalize(r.getTitle()))
                .collect(Collectors.toSet());
        Map<Long, CategoryEntity> categoryMap = categories.mapByIdForUser(user.getId());

        Map<String, List<TransactionEntity>> byMerchant = expenses.stream()
                .collect(Collectors.groupingBy(t -> normalize(t.getMerchant()), LinkedHashMap::new, Collectors.toList()));

        List<Candidate> candidates = new ArrayList<>();
        byMerchant.forEach((key, group) -> {
            if (isRegistered(key, registered)) return;
            toCandidate(group, endMonth, categoryMap).ifPresent(candidates::add);
        });
        candidates.sort(Comparator.comparingDouble(Candidate::confidence).reversed().thenComparing(Candidate::merchant));
        return candidates;
    }

    private Optional<Candidate> toCandidate(List<TransactionEntity> group, YearMonth endMonth, Map<Long, CategoryEntity> categoryMap) {
        // 한 달에 여러 번 결제된 경우 그 달의 합계를 한 번의 반복으로 본다
        Map<YearMonth, List<TransactionEntity>> byMonth = group.stream()
                .collect(Collectors.groupingBy(t -> YearMonth.from(t.getTransactionDate()), TreeMap::new, Collectors.toList()));
        if (byMonth.size() < MIN_MONTHS) return Optional.empty();

        List<Long> monthlyAmounts = byMonth.values().stream().map(txs -> txs.stream().mapToLong(TransactionEntity::getAmount).sum()).toList();
        double cv = coefficientOfVariation(monthlyAmounts);
        if (cv > MAX_AMOUNT_CV) return Optional.empty();

        List<Integer> days = byMonth.values().stream().map(txs -> txs.getFirst().getTransactionDate().getDayOfMonth()).toList();
        long amount = median(monthlyAmounts);
        int day = (int) median(days.stream().map(Integer::longValue).toList());
        double confidence = confidence(byMonth.size(), cv, standardDeviation(days.stream().map(Integer::doubleValue).toList()));

        String merchant = mostCommon(group.stream().map(TransactionEntity::getMerchant).toList());
        boolean subscription = group.stream()
                .map(t -> categoryMap.get(t.getCategoryId()))
                .anyMatch(c -> c != null && c.getName().contains("구독"));
        Long accountId = mostCommon(group.stream().map(TransactionEntity::getAccountId).filter(Objects::nonNull).toList());

        List<String> months = byMonth.keySet().stream().map(YearMonth::toString).toList();
        String reason = "%d개월 동안 매달 %d일 전후로 약 %,d원씩 결제됐습니다.".formatted(byMonth.size(), day, amount);
        SuggestedRule suggested = new SuggestedRule(merchant, RecurrenceType.MONTHLY, day, endMonth.plusMonths(1).atDay(1),
                amount, subscription ? EventType.SUBSCRIPTION : EventType.ETC, Direction.OUTFLOW, accountId);
        return Optional.of(new Candidate(merchant, byMonth.size(), months, amount, day, confidence, reason, suggested));
    }

    /**
     * 0~1. 오래 반복될수록, 금액이 일정할수록, 결제일이 일정할수록 높다.
     * 3개월 동안 금액과 날짜가 완벽히 같으면 0.8, 6개월이면 1.0.
     */
    static double confidence(int months, double amountCv, double dayStdDev) {
        double monthScore = Math.min(1.0, months / (double) LOOKBACK_MONTHS);
        double amountScore = Math.max(0.0, 1.0 - amountCv / MAX_AMOUNT_CV);
        double dayScore = Math.max(0.0, 1.0 - dayStdDev / 7.0);
        double score = 0.4 * monthScore + 0.35 * amountScore + 0.25 * dayScore;
        return Math.round(score * 100) / 100.0;
    }

    /** 상호명 비교용. 공백을 없애고 소문자로 바꾼다. */
    static String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    /** '넷플릭스'와 '넷플릭스 구독'처럼 한쪽이 다른 쪽을 포함해도 같은 것으로 본다. */
    private boolean isRegistered(String merchantKey, Set<String> registeredKeys) {
        return registeredKeys.stream().anyMatch(rule -> !rule.isEmpty() && (rule.contains(merchantKey) || merchantKey.contains(rule)));
    }

    private static double coefficientOfVariation(List<Long> values) {
        double mean = values.stream().mapToLong(Long::longValue).average().orElse(0);
        if (mean == 0) return 0;
        return standardDeviation(values.stream().map(Long::doubleValue).toList()) / mean;
    }

    private static double standardDeviation(List<Double> values) {
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = values.stream().mapToDouble(v -> (v - mean) * (v - mean)).average().orElse(0);
        return Math.sqrt(variance);
    }

    private static long median(List<Long> values) {
        List<Long> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
    }

    private static <T> T mostCommon(List<T> values) {
        return values.stream()
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }
}
