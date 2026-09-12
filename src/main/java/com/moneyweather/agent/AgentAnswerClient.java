package com.moneyweather.agent;

import java.util.Map;

public interface AgentAnswerClient {
    AgentAnswer answer(String userMessage, Map<String, Object> toolSnapshot);

    record AgentAnswer(String content, String provider) {}
}
