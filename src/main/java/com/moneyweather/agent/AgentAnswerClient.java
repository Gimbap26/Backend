package com.moneyweather.agent;

import com.moneyweather.agent.AgentTools.ToolCall;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * 사용자 질문에 답하는 AI. 필요한 데이터는 {@link AgentTools}로 직접 조회하고, 호출한 도구를 답과 함께 돌려준다.
 * 설정({@code money-weather.ai.provider})에 따라 규칙 기반 또는 OpenAI 호환 구현 중 하나가 쓰인다.
 */
public interface AgentAnswerClient {
    AgentAnswer answer(AgentRequest request, AgentTools tools);

    /** 이전 대화 한 줄. role 은 USER 또는 ASSISTANT. */
    record HistoryMessage(String role, String content) {}

    record AgentRequest(String userMessage, List<HistoryMessage> history, LocalDate today, YearMonth baseMonth) {}

    record AgentAnswer(String content, String provider, List<ToolCall> toolCalls) {}
}
