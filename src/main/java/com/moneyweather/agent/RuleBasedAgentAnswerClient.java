package com.moneyweather.agent;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "money-weather.ai.provider", havingValue = "rule-based", matchIfMissing = true)
public class RuleBasedAgentAnswerClient implements AgentAnswerClient {
    @Override
    public AgentAnswer answer(String userMessage, Map<String, Object> toolSnapshot) {
        Map<?, ?> dashboard = (Map<?, ?>) toolSnapshot.get("dashboard");
        String content = "현재 사용 가능 자금은 " + dashboard.get("availableAmount") + "원이고, 자금 날씨는 " + dashboard.get("weather") + "입니다. dashboard, financial-events, forecasts 도구 결과를 근거로 판단했습니다.";
        return new AgentAnswer(content, "rule-based");
    }
}
