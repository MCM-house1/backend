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

    /** 로깅/디버깅용 제공자 이름. 예: "gemini", "mock" */
    String providerName();
}
