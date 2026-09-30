package com.moneyweather.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * 어떤 AI 답변기를 쓸지 정한다({@code money-weather.ai.provider}).
 *
 * <ul>
 *   <li>{@code auto}(기본): API 키가 있으면 외부 AI, 없으면 규칙 기반</li>
 *   <li>{@code rule-based}: 키가 있어도 규칙 기반</li>
 *   <li>{@code openai-compatible}: 외부 AI. 키가 없으면 기동 실패</li>
 * </ul>
 * 선택 결과는 기동 로그에 남긴다(키 값은 남기지 않는다).
 */
@Configuration
public class AgentClientConfig {
    private static final Logger log = LoggerFactory.getLogger(AgentClientConfig.class);

    static final String AUTO = "auto";

    @Bean
    AgentAnswerClient agentAnswerClient(ObjectMapper objectMapper,
                                        @Value("${money-weather.ai.provider:auto}") String provider,
                                        @Value("${money-weather.ai.base-url:https://api.openai.com/v1}") String baseUrl,
                                        @Value("${money-weather.ai.api-key:}") String apiKey,
                                        @Value("${money-weather.ai.model:gpt-4.1-mini}") String model,
                                        @Value("${money-weather.ai.timeout-seconds:60}") long timeoutSeconds) {
        AgentAnswerClient client = select(provider, apiKey, () -> new OpenAiCompatibleAgentAnswerClient(objectMapper, baseUrl, apiKey, model, timeoutSeconds));
        if (client instanceof OpenAiCompatibleAgentAnswerClient) {
            log.info("AI 답변기: {} (model={}, baseUrl={})", OpenAiCompatibleAgentAnswerClient.PROVIDER, model, baseUrl);
        } else {
            log.info("AI 답변기: {} ({})", RuleBasedAgentAnswerClient.PROVIDER,
                    hasText(apiKey) ? "AI_PROVIDER=rule-based 로 지정됨" : "AI_API_KEY 가 없음");
        }
        return client;
    }

    /** 설정값과 키 유무로 답변기를 고른다. 외부 AI 답변기는 필요할 때만 만든다. */
    static AgentAnswerClient select(String provider, String apiKey, Supplier<AgentAnswerClient> openAi) {
        String mode = provider == null ? AUTO : provider.trim().toLowerCase(Locale.ROOT);
        return switch (mode) {
            case AUTO -> hasText(apiKey) ? openAi.get() : new RuleBasedAgentAnswerClient();
            case RuleBasedAgentAnswerClient.PROVIDER -> new RuleBasedAgentAnswerClient();
            case OpenAiCompatibleAgentAnswerClient.PROVIDER -> openAi.get();   // 키가 없으면 생성자에서 실패
            default -> throw new IllegalStateException("AI_PROVIDER 값이 올바르지 않습니다: '" + provider
                    + "'. auto, rule-based, openai-compatible 중 하나를 쓰세요.");
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
