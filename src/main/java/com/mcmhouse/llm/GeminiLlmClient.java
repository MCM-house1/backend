package com.mcmhouse.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Google Gemini (Generative Language API) 호출 구현.
 * 별도 SDK 없이 REST로 직접 호출하므로 pom.xml에 추가 의존성이 필요 없다.
 *
 * <p>무료 등급 키는 https://aistudio.google.com/apikey 에서 카드 등록 없이 발급받을 수 있다.
 */
public class GeminiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiLlmClient.class);
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    /** 모델 과부하(503)나 속도 제한(429)은 흔하고 대개 일시적이라 몇 번 다시 시도한다. */
    private static final int MAX_ATTEMPTS = 3;

    private final LlmProperties props;
    private final RestClient restClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public GeminiLlmClient(LlmProperties props) {
        this.props = props;

        // 응답이 지연되어도 프론트가 무한정 대기하지 않도록 타임아웃을 건다.
        Duration timeout = Duration.ofSeconds(props.getTimeoutSeconds());
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);

        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(factory)
                .build();
    }

    @Override
    public String complete(String prompt) {
        ObjectNode body = buildRequestBody(prompt);
        LlmException last = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                // byte[]로 받는다. 오류 응답은 Content-Type이 application/octet-stream으로 오는 경우가 있어
                // String으로 바로 받으면 변환에 실패하고 상태 코드를 판별할 수 없게 된다.
                byte[] bytes = restClient.post()
                        .uri("/models/{model}:generateContent?key={key}", props.getModel(), props.getApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(byte[].class);
                String raw = bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
                return extractText(raw);

            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                if (!isRetryable(status) || attempt == MAX_ATTEMPTS) {
                    throw new LlmException("Gemini 호출 실패: " + status + " " + e.getStatusText()
                            + " " + e.getResponseBodyAsString(), e);
                }
                // 503(과부하) / 429(속도 제한) / 5xx는 잠깐 뒤 다시 하면 대개 성공한다.
                log.warn("Gemini {} 응답 → {}ms 후 재시도 ({}/{})",
                        status, backoffMillis(attempt), attempt, MAX_ATTEMPTS);
                last = new LlmException("Gemini 호출 실패: " + status + " " + e.getStatusText(), e);
                sleep(backoffMillis(attempt));

            } catch (LlmException e) {
                // 응답은 왔는데 형식이 이상한 경우. 재시도해도 같을 가능성이 높아 바로 올린다.
                throw e;

            } catch (Exception e) {
                // 네트워크 오류, 타임아웃 등.
                if (attempt == MAX_ATTEMPTS) {
                    throw new LlmException("Gemini 호출 실패: " + e.getMessage(), e);
                }
                log.warn("Gemini 통신 오류({}) → {}ms 후 재시도 ({}/{})",
                        e.getMessage(), backoffMillis(attempt), attempt, MAX_ATTEMPTS);
                last = new LlmException("Gemini 호출 실패: " + e.getMessage(), e);
                sleep(backoffMillis(attempt));
            }
        }
        throw last != null ? last : new LlmException("Gemini 호출 실패");
    }

    /** 일시적인 장애만 재시도한다. 400/401/403/404는 다시 해도 결과가 같다. */
    private boolean isRetryable(int status) {
        return status == 429 || status >= 500;
    }

    private long backoffMillis(int attempt) {
        return 500L * attempt;   // 500ms, 1000ms
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new LlmException("재시도 대기 중 중단되었습니다.", ie);
        }
    }

    private ObjectNode buildRequestBody(String prompt) {
        ObjectNode body = mapper.createObjectNode();

        ObjectNode part = mapper.createObjectNode().put("text", prompt);
        ArrayNode parts = mapper.createArrayNode().add(part);
        ObjectNode content = mapper.createObjectNode().set("parts", parts);
        body.set("contents", mapper.createArrayNode().add(content));

        // JSON만 돌려주도록 강제. 모델이 설명문을 덧붙이는 것을 막아 파싱 실패를 줄인다.
        ObjectNode generationConfig = mapper.createObjectNode();
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("temperature", 0.7);
        body.set("generationConfig", generationConfig);

        return body;
    }

    /** Gemini 응답 봉투에서 실제 생성 텍스트만 꺼낸다. */
    private String extractText(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new LlmException("Gemini 응답이 비어 있습니다.");
        }
        try {
            JsonNode root = mapper.readTree(raw);

            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                // 안전 필터에 걸리면 candidates 없이 promptFeedback만 오는 경우가 있다.
                String reason = root.path("promptFeedback").path("blockReason").asText("");
                throw new LlmException("Gemini가 응답을 생성하지 않았습니다."
                        + (reason.isBlank() ? "" : " (blockReason=" + reason + ")"));
            }

            JsonNode parts = candidates.get(0).path("content").path("parts");
            if (!parts.isArray() || parts.isEmpty()) {
                throw new LlmException("Gemini 응답에 본문이 없습니다.");
            }

            String text = parts.get(0).path("text").asText("");
            if (text.isBlank()) {
                throw new LlmException("Gemini 응답 본문이 비어 있습니다.");
            }
            return text;

        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Gemini 응답 파싱 실패. raw={}", raw);
            throw new LlmException("Gemini 응답 파싱 실패: " + e.getMessage(), e);
        }
    }

    @Override
    public String providerName() {
        return "gemini";
    }
}
