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

    /**
     * 응답 속도와 가용성이 안정적인 현행 모델을 기본값으로 둔다.
     *
     * <p>별칭인 gemini-flash-latest는 퇴역 걱정이 없는 대신 트래픽이 몰려 503이 잦다.
     * 반대로 버전을 고정하면 빠르지만 언젠가 퇴역해 404가 난다.
     * 404가 나기 시작하면 아래 명령으로 현재 쓸 수 있는 모델을 확인해 llm.model을 바꾸면 된다.
     *
     * <pre>
     * curl "https://generativelanguage.googleapis.com/v1beta/models?key=발급받은_키"
     * </pre>
     */
    private String model = "gemini-2.5-flash";

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
