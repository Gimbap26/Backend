package com.moneyweather.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** AI_PROVIDER 와 AI_API_KEY 조합에 따라 어떤 답변기를 쓰는지. */
class AgentClientConfigTest {
    private static final String KEY = "sk-test-not-a-real-key";

    private final Supplier<AgentAnswerClient> openAi =
            () -> new OpenAiCompatibleAgentAnswerClient(new ObjectMapper(), "http://localhost:1/v1", KEY, "test-model", 5);

    @Test
    void autoUsesTheRealAiWhenAKeyIsPresent() {
        // IntelliJ 실행 설정처럼 AI_API_KEY 만 넣고 AI_PROVIDER 는 안 넣은 경우
        assertThat(AgentClientConfig.select(null, KEY, openAi)).isInstanceOf(OpenAiCompatibleAgentAnswerClient.class);
        assertThat(AgentClientConfig.select("auto", KEY, openAi)).isInstanceOf(OpenAiCompatibleAgentAnswerClient.class);
        assertThat(AgentClientConfig.select(" AUTO ", KEY, openAi)).isInstanceOf(OpenAiCompatibleAgentAnswerClient.class);
    }

    @Test
    void autoFallsBackToRuleBasedWithoutAKey() {
        assertThat(AgentClientConfig.select("auto", "", openAi)).isInstanceOf(RuleBasedAgentAnswerClient.class);
        assertThat(AgentClientConfig.select("auto", "   ", openAi)).isInstanceOf(RuleBasedAgentAnswerClient.class);
        assertThat(AgentClientConfig.select("auto", null, openAi)).isInstanceOf(RuleBasedAgentAnswerClient.class);
    }

    @Test
    void explicitRuleBasedWinsEvenWithAKey() {
        assertThat(AgentClientConfig.select("rule-based", KEY, openAi)).isInstanceOf(RuleBasedAgentAnswerClient.class);
    }

    @Test
    void explicitOpenAiWithoutAKeyFailsFast() {
        Supplier<AgentAnswerClient> noKey =
                () -> new OpenAiCompatibleAgentAnswerClient(new ObjectMapper(), "http://localhost:1/v1", "", "test-model", 5);
        assertThatThrownBy(() -> AgentClientConfig.select("openai-compatible", "", noKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI_API_KEY");
    }

    @Test
    void typoInProviderFailsFastWithTheAllowedValues() {
        assertThatThrownBy(() -> AgentClientConfig.select("openai", KEY, openAi))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auto, rule-based, openai-compatible");
    }
}
