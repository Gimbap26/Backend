package com.moneyweather.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneyweather.agent.AgentTools.ToolCall;
import com.moneyweather.agent.AgentTools.ToolSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.*;

/**
 * OpenAI Chat Completions 호환 API 로 Tool Calling 을 수행한다.
 *
 * <ol>
 *   <li>system 프롬프트 + 이전 대화 + 질문 + 도구 정의를 보낸다.</li>
 *   <li>응답에 {@code tool_calls}가 있으면 도구를 실행하고, 그 {@code tool_calls}를 담은 assistant 메시지와
 *       {@code tool_call_id}로 짝지은 tool 메시지들을 덧붙여 다시 보낸다.</li>
 *   <li>도구 호출 없이 답이 오면 끝낸다. 최대 {@value #MAX_ROUNDS}번 반복하고, 마지막에는 도구 없이 답하게 한다.</li>
 * </ol>
 * 호출이 실패하면 경고 로그를 남기고 규칙 기반 답변으로 대신하며, provider 에 {@code -fallback}을 붙여 알린다.
 */
public class OpenAiCompatibleAgentAnswerClient implements AgentAnswerClient {
    static final String PROVIDER = "openai-compatible";
    static final int MAX_ROUNDS = 5;

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleAgentAnswerClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final RuleBasedAgentAnswerClient fallback = new RuleBasedAgentAnswerClient();

    /** 설정값은 {@link AgentClientConfig}가 넘겨준다. */
    public OpenAiCompatibleAgentAnswerClient(ObjectMapper objectMapper, String baseUrl, String apiKey, String model, long timeoutSeconds) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("AI_PROVIDER=openai-compatible 이면 AI_API_KEY 가 필요합니다. "
                    + "키 없이 실행하려면 AI_PROVIDER 를 지정하지 않거나(auto) rule-based 로 두세요.");
        }
        this.objectMapper = objectMapper;
        this.model = model;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    @Override
    public AgentAnswer answer(AgentRequest request, AgentTools tools) {
        List<ToolCall> calls = new ArrayList<>();
        try {
            List<Map<String, Object>> messages = initialMessages(request);
            List<Map<String, Object>> toolDefinitions = toolDefinitions(tools.specs());

            for (int round = 1; round <= MAX_ROUNDS; round++) {
                boolean lastRound = round == MAX_ROUNDS;
                JsonNode message = complete(messages, toolDefinitions, lastRound);
                JsonNode toolCalls = message.path("tool_calls");

                if (!toolCalls.isArray() || toolCalls.isEmpty()) {
                    String content = message.path("content").asText("");
                    if (content.isBlank()) throw new IllegalStateException("AI 응답에 내용이 없습니다.");
                    return new AgentAnswer(content, PROVIDER, calls);
                }

                messages.add(assistantWithToolCalls(message));
                for (JsonNode toolCall : toolCalls) {
                    String id = toolCall.path("id").asText();
                    String name = toolCall.path("function").path("name").asText();
                    ToolCall call = tools.invoke(name, parseArguments(toolCall.path("function").path("arguments").asText("{}")));
                    calls.add(call);
                    messages.add(Map.of("role", "tool", "tool_call_id", id, "content", toolResultContent(call)));
                }
            }
            throw new IllegalStateException("도구 호출이 " + MAX_ROUNDS + "번을 넘었습니다.");
        } catch (Exception e) {
            log.warn("AI provider call failed; answering with rule-based fallback: {}", e.toString());
            AgentAnswer fallbackAnswer = fallback.answer(request, tools);
            List<ToolCall> all = new ArrayList<>(calls);
            all.addAll(fallbackAnswer.toolCalls());
            return new AgentAnswer(fallbackAnswer.content(), PROVIDER + "-fallback", all);
        }
    }

    private List<Map<String, Object>> initialMessages(AgentRequest request) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt(request)));
        for (HistoryMessage past : request.history()) {
            String role = "ASSISTANT".equalsIgnoreCase(past.role()) ? "assistant" : "user";
            messages.add(Map.of("role", role, "content", past.content()));
        }
        messages.add(Map.of("role", "user", "content", request.userMessage()));
        return messages;
    }

    static String systemPrompt(AgentRequest request) {
        return """
                너는 개인 재무 앱 '자금 날씨'의 한국어 비서다.
                - 사용자의 잔액, 예정 지출, 예산, 거래 데이터는 반드시 제공된 도구로 조회한다.
                - 답변의 모든 금액과 날짜는 도구 결과에서 가져온다. 도구 결과에 없는 내용은 추측하지 말고 모른다고 말한다.
                - 도구가 오류를 돌려주면 그 이유를 사용자에게 짧게 알린다.
                - 금액은 원 단위로 천 단위 쉼표를 붙여 쓴다. 자금 날씨는 SUNNY=맑음, CLOUDY=흐림, RAINY=비, STORM=폭풍으로 부른다.
                - 3~5문장으로 짧게, 필요하면 바로 할 수 있는 행동을 하나 제안한다.
                오늘 날짜: %s
                사용자의 기준 월: %s (기간을 말하지 않으면 이 달을 기준으로 한다)
                """.formatted(request.today(), request.baseMonth());
    }

    private JsonNode complete(List<Map<String, Object>> messages, List<Map<String, Object>> toolDefinitions, boolean forceAnswer) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("tools", toolDefinitions);
        body.put("tool_choice", forceAnswer ? "none" : "auto");
        JsonNode response = restClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        JsonNode message = response == null ? null : response.path("choices").path(0).path("message");
        if (message == null || message.isMissingNode()) throw new IllegalStateException("AI 응답 형식이 올바르지 않습니다.");
        return message;
    }

    /** 모델이 보낸 tool_calls 를 그대로 되돌려 보내야 이어지는 tool 메시지와 짝이 맞는다. */
    private Map<String, Object> assistantWithToolCalls(JsonNode message) {
        Map<String, Object> assistant = new LinkedHashMap<>();
        assistant.put("role", "assistant");
        assistant.put("content", message.path("content").isNull() ? null : message.path("content").asText(null));
        assistant.put("tool_calls", objectMapper.convertValue(message.path("tool_calls"), new TypeReference<List<Object>>() {}));
        return assistant;
    }

    private List<Map<String, Object>> toolDefinitions(List<ToolSpec> specs) {
        return specs.stream()
                .map(spec -> Map.<String, Object>of("type", "function", "function", Map.of(
                        "name", spec.name(), "description", spec.description(), "parameters", spec.parameters())))
                .toList();
    }

    private Map<String, Object> parseArguments(String json) {
        try {
            return json == null || json.isBlank() ? Map.of() : objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            // 모델이 잘못된 JSON 을 보내면 인자 없이 호출하고, 도구의 기본값을 쓴다
            log.debug("Could not parse tool arguments: {}", json);
            return Map.of();
        }
    }

    private String toolResultContent(ToolCall call) {
        try {
            return objectMapper.writeValueAsString(call.failed() ? Map.of("error", call.error()) : call.result());
        } catch (Exception e) {
            return "{\"error\":\"도구 결과를 변환하지 못했습니다.\"}";
        }
    }
}
