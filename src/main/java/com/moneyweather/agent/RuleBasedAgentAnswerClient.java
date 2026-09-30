package com.moneyweather.agent;

import com.moneyweather.agent.AgentTools.ToolCall;
import com.moneyweather.domain.Enums.WeatherStatus;
import com.moneyweather.service.AlertService.Alert;
import com.moneyweather.service.RecurringDetectionService.Candidate;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * API 키 없이 동작하는 기본 답변기. 질문의 키워드로 의도를 나누고, LLM 과 같은 도구를 호출해 실제 수치로 답한다.
 * OpenAI 호환 클라이언트가 실패했을 때의 대체 답변으로도 쓰인다. 어느 것을 쓸지는 {@link AgentClientConfig}가 정한다.
 */
public class RuleBasedAgentAnswerClient implements AgentAnswerClient {
    static final String PROVIDER = "rule-based";

    enum Intent { SIMULATE, BUDGET, RECURRING, SPENDING, RISK, AVAILABLE, SUMMARY }

    private static final Pattern AMOUNT = Pattern.compile("(\\d[\\d,]*(?:\\.\\d+)?)\\s*(억|천만|백만|만|천)?\\s*(원)?");
    private static final Pattern MONTH_DAY = Pattern.compile("(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일");
    private static final Pattern YEAR_MONTH = Pattern.compile("(\\d{4})\\s*년\\s*(\\d{1,2})\\s*월");
    /** "3월"만 있는 경우. "3월 12일"처럼 날짜의 일부인 것은 제외한다. */
    private static final Pattern MONTH_ONLY = Pattern.compile("(?<!\\d)(\\d{1,2})\\s*월(?!\\s*\\d{1,2}\\s*일)");
    private static final Pattern DAY_ONLY = Pattern.compile("(?<!월\\s?)(?<!\\d)(\\d{1,2})\\s*일");

    @Override
    public AgentAnswer answer(AgentRequest request, AgentTools tools) {
        List<ToolCall> calls = new ArrayList<>();
        String content = switch (classify(request.userMessage())) {
            case SIMULATE -> simulate(request, tools, calls);
            case BUDGET -> budget(request, tools, calls);
            case RECURRING -> recurring(tools, calls);
            case SPENDING -> spending(request, tools, calls);
            case RISK -> risk(tools, calls);
            case AVAILABLE -> available(request, tools, calls);
            case SUMMARY -> summary(tools, calls);
        };
        return new AgentAnswer(content, PROVIDER, calls);
    }

    // --- 의도 분류 -----------------------------------------------------------

    static Intent classify(String message) {
        String text = message == null ? "" : message;
        if (parseAmount(text) != null && containsAny(text, "쓰면", "사면", "써도", "사도", "결제하면", "지출하면", "구매", "살까", "쓸까")) {
            return Intent.SIMULATE;
        }
        if (containsAny(text, "예산", "한도", "초과")) return Intent.BUDGET;
        if (containsAny(text, "반복", "구독", "고정비", "정기", "매달 나가")) return Intent.RECURRING;
        if (containsAny(text, "소비", "어디에", "많이 썼", "많이 쓴", "카테고리", "분석", "지출 내역")) return Intent.SPENDING;
        if (containsAny(text, "위험", "부족", "최저", "마이너스", "괜찮", "언제", "날짜")) return Intent.RISK;
        if (containsAny(text, "얼마", "여유", "써도", "쓸 수", "남은", "가용")) return Intent.AVAILABLE;
        return Intent.SUMMARY;
    }

    private static boolean containsAny(String text, String... keywords) {
        return Arrays.stream(keywords).anyMatch(text::contains);
    }

    /** "25만원", "250,000원", "1.5만 원", "3천원" 같은 표현을 원 단위로 바꾼다. 단위나 '원'이 없으면 금액으로 보지 않는다. */
    static Long parseAmount(String text) {
        Matcher m = AMOUNT.matcher(text);
        while (m.find()) {
            String unit = m.group(2);
            boolean won = m.group(3) != null;
            if (unit == null && !won) continue;
            double number = Double.parseDouble(m.group(1).replace(",", ""));
            long multiplier = switch (unit == null ? "" : unit) {
                case "억" -> 100_000_000L;
                case "천만" -> 10_000_000L;
                case "백만" -> 1_000_000L;
                case "만" -> 10_000L;
                case "천" -> 1_000L;
                default -> 1L;
            };
            return Math.round(number * multiplier);
        }
        return null;
    }

    /**
     * 질문이 가리키는 달. "2027년 3월", "3월"(기준 월의 연도), "지난달·저번 달", "다음 달"을 읽는다.
     * 달을 말하지 않았으면 null — 도구가 기준 월을 쓴다.
     */
    static YearMonth parseMonth(String text, YearMonth baseMonth) {
        Matcher ym = YEAR_MONTH.matcher(text);
        if (ym.find()) return validMonth(Integer.parseInt(ym.group(1)), Integer.parseInt(ym.group(2)));
        if (containsAny(text, "지난달", "지난 달", "저번 달", "저번달", "전달")) return baseMonth.minusMonths(1);
        if (containsAny(text, "다음 달", "다음달")) return baseMonth.plusMonths(1);
        Matcher m = MONTH_ONLY.matcher(text);
        if (m.find()) return validMonth(baseMonth.getYear(), Integer.parseInt(m.group(1)));
        return null;
    }

    private static YearMonth validMonth(int year, int month) {
        return month >= 1 && month <= 12 ? YearMonth.of(year, month) : null;
    }

    /** 달을 말했으면 도구 인자로 넘긴다. */
    private static Map<String, Object> monthArgs(AgentRequest request) {
        YearMonth month = parseMonth(request.userMessage(), request.baseMonth());
        return month == null ? Map.of() : Map.of("month", month.toString());
    }

    /** "9월 12일" 또는 "12일"을 기준 월의 날짜로 바꾼다. */
    static LocalDate parseDate(String text, YearMonth baseMonth) {
        Matcher md = MONTH_DAY.matcher(text);
        if (md.find()) {
            YearMonth month = YearMonth.of(baseMonth.getYear(), Integer.parseInt(md.group(1)));
            return month.atDay(Math.min(Integer.parseInt(md.group(2)), month.lengthOfMonth()));
        }
        Matcher d = DAY_ONLY.matcher(text);
        if (d.find()) {
            int day = Integer.parseInt(d.group(1));
            if (day >= 1 && day <= baseMonth.lengthOfMonth()) return baseMonth.atDay(day);
        }
        return null;
    }

    // --- 의도별 답변 ----------------------------------------------------------

    private String simulate(AgentRequest request, AgentTools tools, List<ToolCall> calls) {
        long amount = parseAmount(request.userMessage());
        LocalDate date = Optional.ofNullable(parseDate(request.userMessage(), request.baseMonth())).orElse(defaultDay(request));
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("amount", amount);
        args.put("spendingDate", date.toString());
        args.put("targetDate", YearMonth.from(date).atEndOfMonth().toString());
        ToolCall call = call(tools, calls, "simulate_spending", args);
        if (call.failed()) return failure(call);
        Map<String, Object> result = map(call.result());
        return result.get("message") + " " + adviceFor(weather(map(result.get("after")).get("weather")));
    }

    private String budget(AgentRequest request, AgentTools tools, List<ToolCall> calls) {
        ToolCall call = call(tools, calls, "get_budget_status", monthArgs(request));
        if (call.failed()) return failure(call);
        Map<String, Object> status = map(call.result());
        if (!status.containsKey("categories")) {
            return "%s에는 설정된 예산이 없습니다. 예산을 먼저 설정하면 초과 여부를 알려드릴게요.".formatted(status.get("month"));
        }

        StringBuilder answer = new StringBuilder("%s 예산 %s 중 %s을 썼습니다(남은 한도 %s)."
                .formatted(status.get("month"), won(status.get("totalLimit")), won(status.get("totalSpent")), won(status.get("totalRemaining"))));
        List<Map<String, Object>> rows = list(status.get("categories"));
        List<String> exceeded = rows.stream().filter(r -> Boolean.TRUE.equals(r.get("exceeded")))
                .map(r -> "%s(%s 초과)".formatted(r.get("category"), won(num(r.get("spent")) - num(r.get("limit"))))).toList();
        if (!exceeded.isEmpty()) {
            answer.append(" 한도를 넘은 항목은 ").append(String.join(", ", exceeded)).append("입니다. 남은 기간 이 항목의 지출을 멈추는 게 좋아요.");
        } else {
            rows.stream().filter(r -> r.get("limit") != null && num(r.get("limit")) > 0)
                    .max(Comparator.comparingDouble(r -> num(r.get("spent")) / (double) num(r.get("limit"))))
                    .ifPresent(r -> answer.append(" 한도를 넘은 항목은 없고, 가장 많이 쓴 비율은 %s로 한도의 %d%%입니다."
                            .formatted(r.get("category"), Math.round(num(r.get("spent")) * 100.0 / num(r.get("limit"))))));
        }
        return answer.toString();
    }

    private String recurring(AgentTools tools, List<ToolCall> calls) {
        ToolCall call = call(tools, calls, "detect_recurring", Map.of());
        if (call.failed()) return failure(call);
        List<Candidate> candidates = list(map(call.result()).get("candidates"));
        if (candidates.isEmpty()) return "최근 6개월 거래에서 반복 규칙으로 등록되지 않은 반복 지출은 찾지 못했습니다.";
        String items = String.join(", ", candidates.stream()
                .map(c -> "%s 월 %s(매달 %d일 전후)".formatted(c.merchant(), won(c.typicalAmount()), c.typicalDayOfMonth())).toList());
        return "매달 반복되는데 아직 반복 규칙으로 등록되지 않은 지출이 %d건 있습니다: %s. 반복 규칙으로 등록하면 앞으로의 자금 날씨 예측에 자동으로 반영됩니다."
                .formatted(candidates.size(), items);
    }

    private String spending(AgentRequest request, AgentTools tools, List<ToolCall> calls) {
        ToolCall call = call(tools, calls, "analyze_spending", monthArgs(request));
        if (call.failed()) return failure(call);
        Map<String, Object> summary = map(call.result());
        List<Map<String, Object>> ranking = list(summary.get("ranking"));
        if (ranking.isEmpty()) return summary.get("month") + "에 기록된 지출이 없습니다.";
        String top = String.join(", ", ranking.stream().limit(3)
                .map(r -> "%s %s(%s%%)".formatted(r.get("category"), won(r.get("amount")), r.get("share"))).toList());
        return "%s 지출은 총 %s입니다. 많이 쓴 순서는 %s입니다.".formatted(summary.get("month"), won(summary.get("totalExpense")), top);
    }

    private String risk(AgentTools tools, List<ToolCall> calls) {
        ToolCall dashboardCall = call(tools, calls, "get_dashboard", Map.of());
        ToolCall forecastCall = call(tools, calls, "get_forecast", Map.of());
        ToolCall alertCall = call(tools, calls, "get_alerts", Map.of());
        if (dashboardCall.failed()) return failure(dashboardCall);
        if (forecastCall.failed()) return failure(forecastCall);

        Map<String, Object> dashboard = map(dashboardCall.result());
        Map<String, Object> forecast = map(forecastCall.result());
        WeatherStatus weather = weather(dashboard.get("weather"));
        StringBuilder answer = new StringBuilder("이번 달 자금 날씨는 %s이고, %s 잔액이 가장 적은 날은 %s로 %s까지 내려갑니다."
                .formatted(label(weather), map(dashboard.get("riskSummary")).get("message"),
                        forecast.get("minimumBalanceDate"), won(forecast.get("minimumExpectedBalance"))));
        if (!alertCall.failed()) {
            List<Alert> alerts = list(map(alertCall.result()).get("alerts"));
            if (alerts.isEmpty()) {
                answer.append(" 지금 따로 주의할 알림은 없습니다.");
            } else {
                answer.append(" 주의할 점: ").append(String.join(" ", alerts.stream().limit(3).map(Alert::message).toList()));
            }
        }
        return answer.toString();
    }

    private String available(AgentRequest request, AgentTools tools, List<ToolCall> calls) {
        ToolCall dashboardCall = call(tools, calls, "get_dashboard", Map.of());
        ToolCall forecastCall = call(tools, calls, "get_forecast", Map.of());
        if (dashboardCall.failed()) return failure(dashboardCall);

        Map<String, Object> dashboard = map(dashboardCall.result());
        long available = num(dashboard.get("availableAmount"));
        StringBuilder answer = new StringBuilder("이번 달 쓸 수 있는 돈은 %s이고 자금 날씨는 %s입니다. 현재 잔액 %s에서 앞으로 나갈 고정 지출 %s을 뺀 금액이에요."
                .formatted(won(available), label(weather(dashboard.get("weather"))), won(dashboard.get("currentBalance")), won(dashboard.get("fixedOutflows"))));
        long days = remainingDays(request);
        if (days > 0 && available > 0) {
            answer.append(" 월말까지 %d일 남아 하루 약 %s씩 쓸 수 있습니다.".formatted(days, won(available / days)));
        } else if (available <= 0) {
            answer.append(" 고정 지출을 감당하기에 잔액이 부족하니 추가 지출은 피하세요.");
        }
        if (!forecastCall.failed()) {
            Map<String, Object> forecast = map(forecastCall.result());
            answer.append(" 가장 빠듯한 날은 %s(%s)입니다.".formatted(forecast.get("minimumBalanceDate"), won(forecast.get("minimumExpectedBalance"))));
        }
        return answer.toString();
    }

    private String summary(AgentTools tools, List<ToolCall> calls) {
        ToolCall call = call(tools, calls, "get_dashboard", Map.of());
        if (call.failed()) return failure(call);
        Map<String, Object> dashboard = map(call.result());
        return "현재 잔액은 %s, 이번 달 쓸 수 있는 돈은 %s으로 자금 날씨는 %s입니다. %s 더 구체적으로 물어보시면(예: '25만원 쓰면 어떻게 돼?', '예산 얼마 남았어?', '구독료 정리해줘') 자세히 알려드릴게요."
                .formatted(won(dashboard.get("currentBalance")), won(dashboard.get("availableAmount")),
                        label(weather(dashboard.get("weather"))), map(dashboard.get("riskSummary")).get("message"));
    }

    // --- 보조 ---------------------------------------------------------------

    private static ToolCall call(AgentTools tools, List<ToolCall> calls, String name, Map<String, Object> args) {
        ToolCall call = tools.invoke(name, args);
        calls.add(call);
        return call;
    }

    private static String failure(ToolCall call) {
        return "데이터를 불러오지 못해 답을 드리기 어렵습니다: " + call.error();
    }

    /** 기준 월이 이번 달이면 오늘, 아니면 기준 월 1일. */
    private static LocalDate defaultDay(AgentRequest request) {
        return YearMonth.from(request.today()).equals(request.baseMonth()) ? request.today() : request.baseMonth().atDay(1);
    }

    /** 기준 월의 남은 날수(오늘 포함). 기준 월이 아직 오지 않았으면 그 달 전체. */
    private static long remainingDays(AgentRequest request) {
        LocalDate end = request.baseMonth().atEndOfMonth();
        LocalDate start = request.today().isBefore(request.baseMonth().atDay(1)) ? request.baseMonth().atDay(1) : request.today();
        return start.isAfter(end) ? 0 : ChronoUnit.DAYS.between(start, end) + 1;
    }

    static String label(WeatherStatus weather) {
        return weather == null ? "알 수 없음" : weather.label();
    }

    private static String adviceFor(WeatherStatus weather) {
        if (weather == null) return "";
        return switch (weather) {
            case SUNNY -> "여유가 있어 계획대로 써도 괜찮습니다.";
            case CLOUDY -> "쓸 수는 있지만 다가오는 고정 지출을 한 번 확인하세요.";
            case RAINY -> "이 지출은 미루거나 줄이는 것을 권합니다.";
            case STORM -> "생활비가 부족해질 수 있어 이 지출은 피하는 게 좋습니다.";
        };
    }

    private static WeatherStatus weather(Object value) {
        if (value instanceof WeatherStatus status) return status;
        return value == null ? null : WeatherStatus.valueOf(value.toString());
    }

    private static String won(Object amount) {
        return "%,d원".formatted(num(amount));
    }

    private static long num(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> list(Object value) {
        return value instanceof List<?> l ? (List<T>) l : List.of();
    }
}
