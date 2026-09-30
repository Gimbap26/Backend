package com.moneyweather.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 LLM 경로(OpenAI 호환 Tool Calling)를 가짜 AI 서버로 검증한다.
 * 모델 응답을 미리 정해 두고, 서버가 받은 요청을 기록해 OpenAI 요청 형식이 맞는지 확인한다.
 */
/*
 * provider 는 기본값 auto: IntelliJ 실행 설정처럼 AI_API_KEY 만 넣으면 외부 AI 를 쓰는지 확인한다.
 * (테스트 기본 설정은 rule-based 로 고정돼 있으므로 여기서 auto 로 되돌린다)
 */
@SpringBootTest(properties = {
        "money-weather.ai.provider=auto",
        "money-weather.ai.api-key=test-key",
        "money-weather.ai.model=test-model"
})
@Import(DemoUserTokenConfig.class)
class OpenAiToolCallingTest extends ApiTestSupport {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final FakeAiServer server = FakeAiServer.start();

    @DynamicPropertySource
    static void aiBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("money-weather.ai.base-url", () -> "http://localhost:" + server.port() + "/v1");
    }

    @BeforeEach
    void resetServer() {
        server.reset();
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }

    private String chat(Long conversationId, String message) throws Exception {
        String conv = conversationId == null ? "null" : conversationId.toString();
        return body(postJson("/api/v1/agent/chat", """
                {"conversationId":%s,"message":"%s"}
                """.formatted(conv, message)).andExpect(status().isOk()));
    }

    private static String toolCallResponse(String id, String name, String argumentsJson) throws IOException {
        return JSON.writeValueAsString(JSON.readTree("""
                {"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[
                  {"id":"%s","type":"function","function":{"name":"%s","arguments":%s}}]}}]}
                """.formatted(id, name, JSON.writeValueAsString(argumentsJson))));
    }

    private static String finalResponse(String content) throws IOException {
        return JSON.writeValueAsString(JSON.readTree("""
                {"choices":[{"message":{"role":"assistant","content":%s}}]}
                """.formatted(JSON.writeValueAsString(content))));
    }

    @Test
    void modelCallsToolThenAnswersWithItsResult() throws Exception {
        server.enqueue(toolCallResponse("call_1", "get_dashboard", "{}"));
        server.enqueue(finalResponse("이번 달 쓸 수 있는 돈은 561,000원입니다."));

        String response = chat(null, "이번 달 얼마 써도 돼?");
        assertThat((String) read(response, "$.provider")).isEqualTo("openai-compatible");
        assertThat((String) read(response, "$.answer")).isEqualTo("이번 달 쓸 수 있는 돈은 561,000원입니다.");
        assertThat((List<String>) read(response, "$.sources")).containsExactly("get_dashboard");

        // 첫 요청: 키, 모델, system 프롬프트, 질문, 도구 8개
        assertThat(server.requests()).hasSize(2);
        assertThat(server.authorizationHeaders()).allMatch("Bearer test-key"::equals);
        JsonNode first = server.requests().getFirst();
        assertThat(first.path("model").asText()).isEqualTo("test-model");
        assertThat(first.path("messages").path(0).path("role").asText()).isEqualTo("system");
        assertThat(last(first.path("messages")).path("content").asText()).isEqualTo("이번 달 얼마 써도 돼?");
        assertThat(first.path("tools")).hasSize(8);
        assertThat(first.path("tool_choice").asText()).isEqualTo("auto");

        // 두 번째 요청: 모델의 tool_calls 를 담은 assistant 메시지 뒤에, 같은 id 로 짝지은 tool 메시지가 온다
        JsonNode messages = server.requests().get(1).path("messages");
        JsonNode assistant = messages.get(messages.size() - 2);
        JsonNode tool = last(messages);
        assertThat(assistant.path("role").asText()).isEqualTo("assistant");
        assertThat(assistant.path("tool_calls").path(0).path("id").asText()).isEqualTo("call_1");
        assertThat(tool.path("role").asText()).isEqualTo("tool");
        assertThat(tool.path("tool_call_id").asText()).isEqualTo("call_1");
        assertThat(JSON.readTree(tool.path("content").asText()).path("availableAmount").asLong()).isEqualTo(561_000);
    }

    @Test
    void toolArgumentsFromTheModelAreUsedAndKeptAsEvidence() throws Exception {
        server.enqueue(toolCallResponse("call_sim", "simulate_spending", "{\"amount\":250000,\"spendingDate\":\"2026-09-12\"}"));
        server.enqueue(finalResponse("9/12에 25만원을 쓰면 쓸 수 있는 돈이 311,000원이 됩니다."));

        long messageId = readLong(chat(null, "12일에 25만원 써도 돼?"), "$.messageId");

        JsonNode tool = last(server.requests().get(1).path("messages"));
        assertThat(JSON.readTree(tool.path("content").asText()).path("after").path("availableAmount").asLong()).isEqualTo(311_000);

        String evidence = getOk("/api/v1/agent/evidence/" + messageId);
        assertThat((String) read(evidence, "$.provider")).isEqualTo("openai-compatible");
        assertThat(readLong(evidence, "$.toolCalls[0].arguments.amount")).isEqualTo(250_000);
        assertThat(readLong(evidence, "$.toolCalls[0].result.after.availableAmount")).isEqualTo(311_000);
    }

    @Test
    void previousTurnsAreSentAsHistory() throws Exception {
        server.enqueue(finalResponse("첫 번째 답입니다."));
        long conversationId = readLong(chat(null, "첫 질문"), "$.conversationId");

        server.enqueue(finalResponse("두 번째 답입니다."));
        chat(conversationId, "두 번째 질문");

        JsonNode messages = server.requests().get(1).path("messages");
        List<String> contents = new ArrayList<>();
        messages.forEach(m -> contents.add(m.path("role").asText() + ":" + m.path("content").asText()));
        assertThat(contents).containsSubsequence("user:첫 질문", "assistant:첫 번째 답입니다.", "user:두 번째 질문");
    }

    @Test
    void toolErrorIsPassedToTheModelInsteadOfFailingTheChat() throws Exception {
        server.enqueue(toolCallResponse("call_bad", "get_forecast", "{\"from\":\"2026-09-30\",\"to\":\"2026-09-01\"}"));
        server.enqueue(finalResponse("기간이 잘못돼서 예측할 수 없었습니다."));

        String response = chat(null, "예측 보여줘");
        assertThat((String) read(response, "$.provider")).isEqualTo("openai-compatible");
        assertThat((String) read(response, "$.toolCalls[0].error")).isNotBlank();
        assertThat(last(server.requests().get(1).path("messages")).path("content").asText()).contains("error");
    }

    @Test
    void serverFailureFallsBackToRuleBasedAnswer() throws Exception {
        server.failNextWith(500);

        String response = chat(null, "이번 달 얼마 써도 돼?");
        assertThat((String) read(response, "$.provider")).isEqualTo("openai-compatible-fallback");
        assertThat((String) read(response, "$.answer")).contains("561,000원");
    }

    @Test
    void endlessToolCallingIsCutOffAfterFiveRounds() throws Exception {
        for (int i = 0; i < 5; i++) server.enqueue(toolCallResponse("call_" + i, "get_dashboard", "{}"));

        String response = chat(null, "계속 도구만 부르는 모델");
        assertThat(server.requests()).hasSize(5);
        assertThat(last(server.requests()).path("tool_choice").asText()).isEqualTo("none");
        assertThat((String) read(response, "$.provider")).isEqualTo("openai-compatible-fallback");
    }

    private static JsonNode last(JsonNode array) {
        return array.get(array.size() - 1);
    }

    private static JsonNode last(List<JsonNode> list) {
        return list.getLast();
    }

    /** 정해 둔 응답을 순서대로 돌려주고 받은 요청을 기록하는 가짜 OpenAI 호환 서버. */
    static final class FakeAiServer {
        private final HttpServer http;
        private final Deque<String> responses = new ArrayDeque<>();
        private final List<JsonNode> requests = new ArrayList<>();
        private final List<String> authorizations = new ArrayList<>();
        private Integer failStatus;

        private FakeAiServer(HttpServer http) {
            this.http = http;
        }

        static FakeAiServer start() {
            try {
                HttpServer http = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
                FakeAiServer server = new FakeAiServer(http);
                http.createContext("/v1/chat/completions", exchange -> server.handle(exchange));
                http.start();
                return server;
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        private synchronized void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            requests.add(JSON.readTree(exchange.getRequestBody().readAllBytes()));
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            int status = 200;
            String body;
            if (failStatus != null) {
                status = failStatus;
                failStatus = null;
                body = "{\"error\":{\"message\":\"boom\"}}";
            } else {
                body = responses.isEmpty() ? finalResponse("기본 답변") : responses.poll();
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }

        int port() { return http.getAddress().getPort(); }
        synchronized void enqueue(String response) { responses.add(response); }
        synchronized void failNextWith(int status) { failStatus = status; }
        synchronized List<JsonNode> requests() { return List.copyOf(requests); }
        synchronized List<String> authorizationHeaders() { return List.copyOf(authorizations); }
        synchronized void reset() { responses.clear(); requests.clear(); authorizations.clear(); failStatus = null; }
        void stop() { http.stop(0); }
    }
}
