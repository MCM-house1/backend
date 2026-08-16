package com.mcmhouse.llm;

/**
 * LLM 호출 추상화. 구현체를 갈아끼우면 제공자(Gemini/Claude/OpenAI)를 바꿀 수 있다.
 * 어떤 구현체를 쓸지는 application.yml의 llm.provider 값으로 결정한다.
 */
public interface LlmClient {

    /**
     * 프롬프트를 보내고 모델이 생성한 텍스트를 그대로 돌려준다.
     * JSON 응답이 필요한 경우 프롬프트에서 형식을 지정하고, 파싱은 호출한 쪽에서 한다.
     *
     * @throws LlmException 호출 실패(네트워크, 인증, 응답 형식 이상 등)
     */
    String complete(String prompt);

    /**
     * 이미지와 함께 프롬프트를 보낸다(비전). base64는 data URL 접두사 없는 순수 인코딩 문자열이어야 한다.
     * 이미지 입력을 지원하지 않는 구현체는 예외를 던지며, 호출한 쪽에서 폴백한다.
     *
     * @param prompt      지시문
     * @param base64Image 순수 base64 (예: "/9j/4AAQ...")
     * @param mimeType    예: "image/jpeg", "image/png"
     * @throws LlmException 미지원 또는 호출 실패
     */
    default String completeWithImage(String prompt, String base64Image, String mimeType) {
        throw new LlmException(providerName() + " 제공자는 이미지 입력을 지원하지 않습니다.");
    }

    /** 로깅/디버깅용 제공자 이름. 예: "gemini", "mock" */
    String providerName();
}
