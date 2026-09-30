package com.moneyweather.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneyweather.agent.AgentAnswerClient;
import com.moneyweather.agent.AgentAnswerClient.AgentAnswer;
import com.moneyweather.agent.AgentAnswerClient.AgentRequest;
import com.moneyweather.agent.AgentAnswerClient.HistoryMessage;
import com.moneyweather.agent.AgentTools;
import com.moneyweather.agent.AgentTools.ToolCall;
import com.moneyweather.domain.Enums.WeatherStatus;
import com.moneyweather.domain.entity.ConversationEntity;
import com.moneyweather.domain.entity.MessageEntity;
import com.moneyweather.domain.entity.UserEntity;
import com.moneyweather.repository.ConversationRepository;
import com.moneyweather.repository.MessageRepository;
import com.moneyweather.service.AlertService.Alert;
import com.moneyweather.service.RecurringDetectionService.Candidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * AI Agent 기능: 대화, 답변 근거, 추천 질문, 행동 추천.
 *
 * <p>클래스 전체를 하나의 트랜잭션으로 묶지 않는다. 답변 중 도구가 실패하면(예: 예산 없음) 그 도구의
 * 트랜잭션만 롤백되고, 사용자 메시지 저장과 다른 도구 호출은 그대로 남아야 하기 때문이다.
 * 여기서 부르는 서비스와 저장소 메서드는 각자 트랜잭션을 가진다.
 */
@Service
public class AgentService {
    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    /** 모델에 넘길 이전 대화 수. */
    static final int HISTORY_SIZE = 10;

    private final AgentAnswerClient answerClient;
    private final AgentTools tools;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final CurrentUserService currentUser;
    private final MoneyWeatherService moneyWeather;
    private final BudgetService budgets;
    private final AlertService alerts;
    private final RecurringDetectionService detection;
    private final ObjectMapper objectMapper;

    public AgentService(AgentAnswerClient answerClient, AgentTools tools, ConversationRepository conversations, MessageRepository messages,
                        CurrentUserService currentUser, MoneyWeatherService moneyWeather, BudgetService budgets, AlertService alerts,
                        RecurringDetectionService detection, ObjectMapper objectMapper) {
        this.budgets = budgets;
        this.answerClient = answerClient;
        this.tools = tools;
        this.conversations = conversations;
        this.messages = messages;
        this.currentUser = currentUser;
        this.moneyWeather = moneyWeather;
        this.alerts = alerts;
        this.detection = detection;
        this.objectMapper = objectMapper;
    }

    // --- 대화 ---------------------------------------------------------------

    public Map<String, Object> startConversation(String title) {
        Long userId = currentUser.requireId();
        ConversationEntity conversation = conversations.save(new ConversationEntity(userId, title == null || title.isBlank() ? "새 대화" : title, LocalDateTime.now()));
        return Map.of("conversationId", conversation.getId(), "title", conversation.getTitle(), "createdAt", conversation.getCreatedAt());
    }

    /**
     * 질문에 답한다. 이전 대화 {@value #HISTORY_SIZE}개를 함께 넘기고, AI 가 실제로 호출한 도구와 그 결과를
     * 답변과 함께 저장해 나중에 근거를 그대로 보여줄 수 있게 한다.
     */
    public Map<String, Object> chat(Long conversationId, String message) {
        UserEntity user = currentUser.require();
        ConversationEntity conversation = conversationId == null
                ? conversations.save(new ConversationEntity(user.getId(), "AI 상담", LocalDateTime.now()))
                : requireOwnedConversation(user.getId(), conversationId);

        List<HistoryMessage> history = recentHistory(conversation.getId());
        messages.save(new MessageEntity(conversation.getId(), "USER", truncate(message, MessageEntity.MAX_CONTENT_LENGTH), "", LocalDateTime.now()));

        AgentRequest request = new AgentRequest(message, history, LocalDate.now(), YearMonth.parse(user.getBaseMonth()));
        AgentAnswer answer = answerClient.answer(request, tools);

        List<String> sources = answer.toolCalls().stream().map(ToolCall::name).distinct().toList();
        MessageEntity saved = messages.save(new MessageEntity(conversation.getId(), "ASSISTANT",
                truncate(answer.content(), MessageEntity.MAX_CONTENT_LENGTH), String.join(",", sources),
                LocalDateTime.now(), toolTrace(answer)));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("conversationId", conversation.getId());
        body.put("messageId", saved.getId());
        body.put("answer", saved.getContent());
        body.put("provider", answer.provider());
        body.put("sources", sources);
        body.put("toolCalls", answer.toolCalls().stream().map(this::summary).toList());
        return body;
    }

    public Map<String, Object> messages(long conversationId) {
        ConversationEntity conversation = requireOwnedConversation(currentUser.requireId(), conversationId);
        List<Map<String, Object>> rows = messages.findByConversationIdOrderByCreatedAtAsc(conversation.getId()).stream()
                .map(m -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("messageId", m.getId());
                    row.put("role", m.getRole());
                    row.put("content", m.getContent());
                    row.put("sources", splitSources(m.getSources()));
                    row.put("createdAt", m.getCreatedAt());
                    return row;
                })
                .toList();
        return Map.of("conversationId", conversationId, "messages", rows);
    }

    /** 답변 당시 저장해 둔 도구 호출 기록을 그대로 돌려준다. 다시 계산하지 않는다. */
    public Map<String, Object> evidence(long messageId) {
        MessageEntity message = messages.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found."));
        requireOwnedConversation(currentUser.requireId(), message.getConversationId());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messageId", messageId);
        body.put("role", message.getRole());
        body.put("content", message.getContent());
        body.put("recordedAt", message.getCreatedAt());
        body.put("sources", splitSources(message.getSources()));
        Map<String, Object> trace = parseTrace(message.getToolTrace());
        body.put("provider", trace.get("provider"));
        body.put("truncated", Boolean.TRUE.equals(trace.get("truncated")));
        body.put("toolCalls", trace.getOrDefault("toolCalls", List.of()));
        return body;
    }

    private ConversationEntity requireOwnedConversation(Long userId, long conversationId) {
        ConversationEntity conversation = conversations.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found."));
        currentUser.assertOwner(conversation.getUserId(), userId);
        return conversation;
    }

    private List<HistoryMessage> recentHistory(Long conversationId) {
        List<MessageEntity> all = messages.findByConversationIdOrderByCreatedAtAsc(conversationId);
        return all.subList(Math.max(0, all.size() - HISTORY_SIZE), all.size()).stream()
                .map(m -> new HistoryMessage(m.getRole(), m.getContent()))
                .toList();
    }

    /**
     * 근거로 남길 JSON. 한도를 넘으면 결과 본문을 빼고 도구 이름·인자·오류만 남긴다
     * (JSON 을 중간에서 자르면 읽을 수 없게 되므로).
     */
    private String toolTrace(AgentAnswer answer) {
        try {
            Map<String, Object> full = new LinkedHashMap<>();
            full.put("provider", answer.provider());
            full.put("toolCalls", answer.toolCalls());
            String json = objectMapper.writeValueAsString(full);
            if (json.length() <= MessageEntity.MAX_TOOL_TRACE_LENGTH) return json;

            Map<String, Object> reduced = new LinkedHashMap<>();
            reduced.put("provider", answer.provider());
            reduced.put("truncated", true);
            reduced.put("toolCalls", answer.toolCalls().stream().map(this::summary).toList());
            return truncate(objectMapper.writeValueAsString(reduced), MessageEntity.MAX_TOOL_TRACE_LENGTH);
        } catch (Exception e) {
            log.warn("Could not serialize agent tool trace", e);
            return null;
        }
    }

    private Map<String, Object> parseTrace(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of("truncated", true);
        }
    }

    private Map<String, Object> summary(ToolCall call) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", call.name());
        row.put("arguments", call.arguments());
        row.put("error", call.error());
        row.put("durationMs", call.durationMs());
        return row;
    }

    // --- 분석 도구 (화면에서 직접 부르는 API) ---------------------------------

    public Map<String, Object> analyzeSpending(YearMonth month) {
        return budgets.spendingSummary(month);
    }

    public Map<String, Object> detectRecurring() {
        return Map.of("candidates", detection.detect());
    }

    // --- 추천 질문 ------------------------------------------------------------

    /** 지금 상태에서 물어볼 만한 질문 4개. 위험한 날, 예산 초과, 등록 안 된 반복 지출 순으로 우선한다. */
    public Map<String, Object> suggestions() {
        Snapshot s = snapshot();
        LinkedHashSet<String> questions = new LinkedHashSet<>();

        if (s.minBalance() < AlertService.LOW_BALANCE_THRESHOLD) {
            questions.add("%d월 %d일 잔액이 %s까지 떨어지는데 괜찮을까?".formatted(
                    s.minDate().getMonthValue(), s.minDate().getDayOfMonth(), compactWon(s.minBalance())));
        }
        s.exceededCategories().stream().findFirst().ifPresent(c -> questions.add(c + " 예산을 넘었는데 어떻게 줄일까?"));
        s.nearLimitCategories().stream().findFirst().ifPresent(c -> questions.add(c + " 예산이 얼마 남았어?"));
        s.candidates().stream().findFirst().ifPresent(c -> questions.add(withObjectParticle(c.merchant()) + " 반복 지출로 등록할까?"));
        questions.add("이번 달 하루에 얼마까지 써도 돼?");
        questions.add("이번 달 잔액이 가장 적은 날은 언제야?");
        questions.add("이번 달 어디에 돈을 가장 많이 썼어?");
        questions.add("10만원 더 쓰면 자금 날씨가 어떻게 돼?");

        return Map.of("suggestions", questions.stream().limit(4).toList());
    }

    // --- 행동 추천 ------------------------------------------------------------

    public record Action(String type, int priority, String message, Object detail) {}

    /** 실제 수치를 넣은 이번 달 행동 추천. 급한 것부터 정렬한다. */
    public Map<String, Object> recommendActions() {
        Snapshot s = snapshot();
        List<Action> actions = new ArrayList<>();

        if (s.minBalance() < AlertService.LOW_BALANCE_THRESHOLD || s.weather() == WeatherStatus.RAINY || s.weather() == WeatherStatus.STORM) {
            actions.add(new Action("PROTECT_LOW_BALANCE", 1,
                    "%s에 잔액이 %,d원으로 가장 낮아집니다. 그 전까지 큰 지출은 미루세요.".formatted(s.minDate(), s.minBalance()), null));
        }
        if (s.available() <= 0) {
            actions.add(new Action("COVER_SHORTFALL", 1,
                    "고정 지출을 감당하기에 %,d원이 부족합니다. 비상금에서 옮기거나 지출 일정을 조정하세요.".formatted(-s.available()), null));
        } else if (s.remainingDays() > 0) {
            actions.add(new Action("DAILY_ALLOWANCE", 2,
                    "%s까지 하루 %,d원 이내로 쓰면 지금 자금 날씨를 유지할 수 있습니다.".formatted(s.monthEnd(), s.available() / s.remainingDays()),
                    Map.of("availableAmount", s.available(), "remainingDays", s.remainingDays())));
        }
        s.budgetRows().stream().filter(r -> Boolean.TRUE.equals(r.get("exceeded"))).forEach(r ->
                actions.add(new Action("BUDGET_EXCEEDED", 1, "%s 예산을 %,d원 넘었습니다. 남은 기간 %s 지출을 멈추세요.".formatted(
                        r.get("category"), num(r.get("spent")) - num(r.get("limit")), r.get("category")), r)));
        s.upcomingBills().forEach(a ->
                actions.add(new Action("UPCOMING_BILL", 3, a.message() + " 결제 계좌 잔액을 확인하세요.", a)));
        s.candidates().stream().limit(2).forEach(c ->
                actions.add(new Action("REGISTER_RECURRING", 4, "%s 월 %,d원이 매달 반복됩니다. 반복 규칙으로 등록하면 자금 날씨 예측에 반영됩니다."
                        .formatted(c.merchant(), c.typicalAmount()), c.suggestedRule())));
        if (s.weather() == WeatherStatus.SUNNY && s.available() > 0) {
            actions.add(new Action("SAVE_SURPLUS", 5, "여유가 있습니다. 남는 돈 중 %,d원을 비상금으로 옮겨두면 좋습니다.".formatted(s.available() / 2), null));
        }
        actions.sort(Comparator.comparingInt(Action::priority));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("weather", s.weather());
        body.put("availableAmount", s.available());
        body.put("actions", actions);
        body.put("riskSummary", s.riskSummary());
        return body;
    }

    // --- 공통 상태 ------------------------------------------------------------

    private record Snapshot(WeatherStatus weather, long available, Object riskSummary, long minBalance, LocalDate minDate,
                            LocalDate monthEnd, long remainingDays, List<Map<String, Object>> budgetRows,
                            List<String> exceededCategories, List<String> nearLimitCategories,
                            List<Candidate> candidates, List<Alert> upcomingBills) {}

    @SuppressWarnings("unchecked")
    private Snapshot snapshot() {
        UserEntity user = currentUser.require();
        YearMonth month = YearMonth.parse(user.getBaseMonth());
        Map<String, Object> dashboard = moneyWeather.dashboard(null, null);
        Map<String, Object> forecast = moneyWeather.forecast(month.atDay(1), month.atEndOfMonth());

        List<Map<String, Object>> budgetRows = budgets.exists(month)
                ? (List<Map<String, Object>>) budgets.status(month).get("categories")
                : List.of();
        List<String> exceeded = budgetRows.stream().filter(r -> Boolean.TRUE.equals(r.get("exceeded")))
                .map(r -> (String) r.get("category")).toList();
        List<String> near = budgetRows.stream()
                .filter(r -> r.get("limit") != null && !Boolean.TRUE.equals(r.get("exceeded")))
                .filter(r -> num(r.get("limit")) > 0 && num(r.get("spent")) >= num(r.get("limit")) * AlertService.BUDGET_NEAR_RATIO)
                .map(r -> (String) r.get("category")).toList();
        List<Alert> upcoming = ((List<Alert>) alerts.alerts(null, null).get("alerts")).stream()
                .filter(a -> "UPCOMING_BILL".equals(a.type())).toList();

        return new Snapshot((WeatherStatus) dashboard.get("weather"), num(dashboard.get("availableAmount")), dashboard.get("riskSummary"),
                num(forecast.get("minimumExpectedBalance")), (LocalDate) forecast.get("minimumBalanceDate"),
                month.atEndOfMonth(), remainingDays(month), budgetRows, exceeded, near, detection.detect(), upcoming);
    }

    /** 기준 월의 남은 날수(오늘 포함). 기준 월이 아직 오지 않았으면 그 달 전체, 지났으면 0. */
    static long remainingDays(YearMonth month) {
        LocalDate today = LocalDate.now();
        LocalDate start = today.isBefore(month.atDay(1)) ? month.atDay(1) : today;
        return start.isAfter(month.atEndOfMonth()) ? 0 : ChronoUnit.DAYS.between(start, month.atEndOfMonth()) + 1;
    }

    /** 600,000 → "60만원", 612,300 → "612,300원". */
    static String compactWon(long amount) {
        return amount != 0 && amount % 10_000 == 0 ? "%,d만원".formatted(amount / 10_000) : "%,d원".formatted(amount);
    }

    /** 받침이 있으면 '을', 없으면 '를'. 한글이 아니면 '를'. */
    static String withObjectParticle(String word) {
        if (word == null || word.isEmpty()) return "";
        char last = word.charAt(word.length() - 1);
        boolean hangul = last >= 0xAC00 && last <= 0xD7A3;
        boolean hasFinalConsonant = hangul && (last - 0xAC00) % 28 != 0;
        return word + (hasFinalConsonant ? "을" : "를");
    }

    private static long num(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    private static List<String> splitSources(String sources) {
        if (sources == null || sources.isBlank()) return List.of();
        return Arrays.stream(sources.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
    }

    private static String truncate(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }
}
