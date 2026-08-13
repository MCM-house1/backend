package com.mcmhouse.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM 관련 설정. application.yml의 llm.* 를 바인딩한다.
 *
 * <pre>
 * llm:
 *   provider: gemini        # gemini | mock
 *   api-key: ${GEMINI_API_KEY:}
 *   model: gemini-2.0-flash
 *   timeout-seconds: 20
 * </pre>
 */
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    /** gemini = 실제 호출, mock = 키 없이 로컬 테스트용 고정 응답. */
    private String provider = "gemini";

    /** 비어 있으면 provider 설정과 무관하게 mock으로 동작한다. */
    private String apiKey = "";

    private String model = "gemini-2.0-flash";

    /** 응답이 늦어도 프론트가 하염없이 기다리지 않도록 하는 상한. */
    private int timeoutSeconds = 20;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
