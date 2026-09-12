package com.moneyweather.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "money-weather.ai.provider", havingValue = "openai-compatible")
public class OpenAiCompatibleAgentAnswerClient implements AgentAnswerClient {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;

    public OpenAiCompatibleAgentAnswerClient(
            ObjectMapper objectMapper,
            @Value("${money-weather.ai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${money-weather.ai.api-key:}") String apiKey,
            @Value("${money-weather.ai.model:gpt-4.1-mini}") String model
    ) {
        this.objectMapper = objectMapper;
        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    @Override
    public AgentAnswer answer(String userMessage, Map<String, Object> toolSnapshot) {
        try {
            Map<String, Object> request = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", "You are a Korean personal-finance assistant. Answer briefly and cite the provided tool snapshot."),
                            Map.of("role", "user", "content", userMessage),
                            Map.of("role", "tool", "content", objectMapper.writeValueAsString(toolSnapshot))
                    )
            );
            JsonNode node = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            String content = node.path("choices").path(0).path("message").path("content").asText();
            return new AgentAnswer(content.isBlank() ? fallback(toolSnapshot) : content, "openai-compatible");
        } catch (Exception ignored) {
            return new AgentAnswer(fallback(toolSnapshot), "openai-compatible-fallback");
        }
    }

    private String fallback(Map<String, Object> toolSnapshot) {
        Map<?, ?> dashboard = (Map<?, ?>) toolSnapshot.get("dashboard");
        return "현재 사용 가능 자금은 " + dashboard.get("availableAmount") + "원이고, 자금 날씨는 " + dashboard.get("weather") + "입니다.";
    }
}
