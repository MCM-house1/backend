package com.mcmhouse.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 설정값에 따라 LLM 구현체를 하나 고른다.
 * 제공자를 바꾸려면 application.yml의 llm.provider만 수정하면 된다.
 */
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    @Bean
    public LlmClient llmClient(LlmProperties props) {
        String provider = props.getProvider() == null ? "" : props.getProvider().trim().toLowerCase();

        if ("mock".equals(provider)) {
            log.info("LLM provider = mock (설정에 의해 가짜 응답 사용)");
            return new MockLlmClient();
        }

        // 키가 없으면 어떤 제공자를 지정했든 기동은 되어야 한다. 서버가 죽는 대신 mock으로 내려간다.
        if (!props.hasApiKey()) {
            log.warn("LLM API 키가 비어 있어 mock으로 동작합니다. "
                    + "실제 AI 분석을 쓰려면 환경변수 GEMINI_API_KEY를 설정하세요.");
            return new MockLlmClient();
        }

        if ("gemini".equals(provider)) {
            log.info("LLM provider = gemini (model={})", props.getModel());
            return new GeminiLlmClient(props);
        }

        log.warn("알 수 없는 llm.provider='{}' → mock으로 동작합니다. (지원: gemini, mock)", provider);
        return new MockLlmClient();
    }
}
