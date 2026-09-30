package com.moneyweather.agent;

import com.moneyweather.domain.entity.UserEntity;
import com.moneyweather.service.AlertService;
import com.moneyweather.service.BudgetService;
import com.moneyweather.service.CurrentUserService;
import com.moneyweather.service.MoneyWeatherService;
import com.moneyweather.service.MoneyWeatherService.SimulationRequest;
import com.moneyweather.service.RecurringDetectionService;
import com.moneyweather.service.ScheduleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * AI 가 사용자 데이터를 조회할 때 쓰는 도구 모음. 실제 LLM(Tool Calling)과 규칙 기반 답변이 같은 도구를 쓴다.
 *
 * <p>각 도구는 기존 서비스 메서드에 위임한다. 도구 하나가 실패해도 대화 전체가 실패하지 않도록
 * 예외는 {@link ToolCall#error()}로 돌려주고, AI 는 그 오류 메시지를 보고 답을 조정한다.
 * 도구 호출마다 자체 트랜잭션으로 실행되므로, 한 도구의 실패가 다른 도구나 메시지 저장을 롤백시키지 않는다.
 */
@Component
public class AgentTools {
    private static final Logger log = LoggerFactory.getLogger(AgentTools.class);

    /** OpenAI Chat Completions 의 function 정의와 같은 모양. */
    public record ToolSpec(String name, String description, Map<String, Object> parameters) {}

    /** 도구 호출 한 건의 기록. 답변 근거(evidence)로 그대로 저장된다. */
    public record ToolCall(String name, Map<String, Object> arguments, Object result, String error, long durationMs) {
        public boolean failed() { return error != null; }
    }

    private interface Tool {
        Object run(Map<String, Object> args, UserEntity user);
    }

    private record Registered(ToolSpec spec, Tool tool) {}

    private final Map<String, Registered> tools = new LinkedHashMap<>();
    private final CurrentUserService currentUser;

    public AgentTools(MoneyWeatherService moneyWeather, ScheduleService schedule, BudgetService budgets, AlertService alerts,
                      RecurringDetectionService detection, CurrentUserService currentUser) {
        this.currentUser = currentUser;

        register("get_dashboard",
                "현재 잔액, 이번 달 남은 고정 지출, 사용 가능 자금, 자금 날씨(SUNNY/CLOUDY/RAINY/STORM), 위험도, 다가오는 이벤트를 조회한다.",
                schema(Map.of(), List.of()),
                (args, user) -> moneyWeather.dashboard(null, null));

        register("get_financial_events",
                "기간 내 예정된 입금·지출 이벤트(카드 대금, 통신비, 구독료, 급여 등)를 조회한다.",
                schema(Map.of("from", dateProp("시작일 (기본: 기준 월 1일)"), "to", dateProp("종료일 (기본: 기준 월 말일)")), List.of()),
                (args, user) -> schedule.events(from(args, user), to(args, user), null, null, null));

        register("get_forecast",
                "기간 동안 날짜별 예상 잔액과 잔액이 가장 적어지는 날(최저 잔액일)을 계산한다.",
                schema(Map.of("from", dateProp("시작일 (기본: 기준 월 1일)"), "to", dateProp("종료일 (기본: 기준 월 말일)")), List.of()),
                (args, user) -> moneyWeather.forecast(from(args, user), to(args, user)));

        register("get_budget_status",
                "월 예산 대비 카테고리별 실제 지출, 남은 한도, 초과 여부를 조회한다.",
                schema(Map.of("month", monthProp()), List.of()),
                (args, user) -> {
                    YearMonth month = month(args, user);
                    if (!budgets.exists(month)) {
                        return Map.of("month", month.toString(), "message", "이 달에 설정된 예산이 없습니다.");
                    }
                    return budgets.status(month);
                });

        register("analyze_spending",
                "월 지출을 카테고리별로 합산하고 많이 쓴 순서로 정렬한다.",
                schema(Map.of("month", monthProp()), List.of()),
                (args, user) -> budgets.spendingSummary(month(args, user)));

        register("simulate_spending",
                "특정 날짜에 돈을 더 쓴다고 가정했을 때 사용 가능 자금, 자금 날씨, 최저 잔액이 어떻게 바뀌는지 계산한다. 저장하지 않는다.",
                schema(Map.of(
                        "amount", Map.of("type", "integer", "description", "지출할 금액(원)"),
                        "spendingDate", dateProp("지출할 날짜 (기본: 오늘)"),
                        "targetDate", dateProp("영향을 볼 마지막 날짜 (기본: 지출일이 속한 달 말일)"),
                        "title", Map.of("type", "string", "description", "지출 이름")), List.of("amount")),
                (args, user) -> {
                    LocalDate spendingDate = date(args, "spendingDate", LocalDate.now());
                    LocalDate targetDate = date(args, "targetDate", YearMonth.from(spendingDate).atEndOfMonth());
                    return moneyWeather.simulate(new SimulationRequest(spendingDate, amount(args), targetDate, (String) args.get("title")));
                });

        register("detect_recurring",
                "최근 6개월 거래에서 매달 반복되는데 아직 반복 규칙으로 등록되지 않은 지출을 찾는다.",
                schema(Map.of(), List.of()),
                (args, user) -> Map.of("candidates", detection.detect()));

        register("get_alerts",
                "잔액 부족 예상, 일주일 안의 결제, 예산 초과·임박 알림을 조회한다.",
                schema(Map.of("from", dateProp("시작일 (기본: 기준 월 1일)"), "to", dateProp("종료일 (기본: 기준 월 말일)")), List.of()),
                (args, user) -> alerts.alerts(optionalDate(args, "from"), optionalDate(args, "to")));
    }

    public List<ToolSpec> specs() {
        return tools.values().stream().map(Registered::spec).toList();
    }

    public ToolCall invoke(String name, Map<String, Object> arguments) {
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        long started = System.nanoTime();
        Registered registered = tools.get(name);
        if (registered == null) {
            return new ToolCall(name, args, null, "알 수 없는 도구입니다: " + name, 0);
        }
        try {
            Object result = registered.tool().run(args, currentUser.require());
            return new ToolCall(name, args, result, null, elapsedMs(started));
        } catch (ResponseStatusException e) {
            return new ToolCall(name, args, null, e.getReason(), elapsedMs(started));
        } catch (IllegalArgumentException | DateTimeParseException | ClassCastException e) {
            return new ToolCall(name, args, null, "도구 인자가 올바르지 않습니다: " + e.getMessage(), elapsedMs(started));
        } catch (RuntimeException e) {
            log.warn("Agent tool {} failed", name, e);
            return new ToolCall(name, args, null, "도구 실행 중 오류가 발생했습니다.", elapsedMs(started));
        }
    }

    private void register(String name, String description, Map<String, Object> parameters, Tool tool) {
        tools.put(name, new Registered(new ToolSpec(name, description, parameters), tool));
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    // --- 인자 해석 -----------------------------------------------------------

    private static LocalDate from(Map<String, Object> args, UserEntity user) {
        return date(args, "from", YearMonth.parse(user.getBaseMonth()).atDay(1));
    }

    private static LocalDate to(Map<String, Object> args, UserEntity user) {
        return date(args, "to", YearMonth.from(from(args, user)).atEndOfMonth());
    }

    private static YearMonth month(Map<String, Object> args, UserEntity user) {
        Object value = args.get("month");
        return value == null || value.toString().isBlank() ? YearMonth.parse(user.getBaseMonth()) : YearMonth.parse(value.toString());
    }

    private static LocalDate date(Map<String, Object> args, String key, LocalDate fallback) {
        LocalDate value = optionalDate(args, key);
        return value != null ? value : fallback;
    }

    private static LocalDate optionalDate(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null || value.toString().isBlank() ? null : LocalDate.parse(value.toString());
    }

    private static long amount(Map<String, Object> args) {
        Object value = args.get("amount");
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String text && !text.isBlank()) return Long.parseLong(text.replaceAll("[^0-9]", ""));
        throw new IllegalArgumentException("amount 가 필요합니다.");
    }

    // --- JSON 스키마 ----------------------------------------------------------

    private static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }

    private static Map<String, Object> dateProp(String description) {
        return Map.of("type", "string", "format", "date", "description", description + ", YYYY-MM-DD");
    }

    private static Map<String, Object> monthProp() {
        return Map.of("type", "string", "description", "대상 월 YYYY-MM (기본: 기준 월)");
    }
}
