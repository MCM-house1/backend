package com.mcmhouse.llm;

/**
 * LLM 호출 실패. 서비스 계층에서 이 예외를 잡아 규칙기반 결과로 폴백한다.
 * 즉 이 예외가 사용자에게 그대로 노출되는 일은 없어야 한다.
 */
public class LlmException extends RuntimeException {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
