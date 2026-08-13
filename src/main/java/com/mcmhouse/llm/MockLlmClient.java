package com.mcmhouse.llm;

/**
 * API 키 없이 전체 플로우를 돌려보기 위한 가짜 구현.
 * 실제 모델을 호출하지 않고 형식만 맞는 응답을 즉시 돌려준다.
 *
 * <p>키가 비어 있으면 {@link LlmConfig}가 자동으로 이 구현을 선택하므로,
 * 프론트 연동이나 로컬 테스트는 키 발급 전에도 막힘없이 진행할 수 있다.
 *
 * <p>판별 응답의 house는 의도적으로 null이다. 가짜 구현이 House를 임의로 고르면
 * 잘못된 결과를 진짜처럼 보여주게 되므로, 서비스가 규칙기반 점수 결과로 채우도록 넘긴다.
 */
public class MockLlmClient implements LlmClient {

    /** 프롬프트에 이 표시가 있으면 후속질문 생성 요청으로 본다. */
    public static final String TASK_QUESTIONS = "[TASK:QUESTIONS]";
    /** 프롬프트에 이 표시가 있으면 최종 판별 요청으로 본다. */
    public static final String TASK_ANALYZE = "[TASK:ANALYZE]";

    private static final String QUESTIONS_JSON = """
            {
              "questions": [
                "지금까지의 선택을 보면 당신만의 기준이 뚜렷해 보여요. 최근에 '이건 나답다'고 느꼈던 물건이나 순간이 있다면 자유롭게 들려주세요.",
                "새로운 공간에 들어섰을 때, 가장 먼저 마음이 움직이는 순간은 언제인가요? 떠오르는 대로 편하게 적어주세요."
              ]
            }
            """;

    private static final String ANALYZE_JSON = """
            {
              "house": null,
              "summary": "샘플 응답입니다. 실제 AI 분석 결과가 아닙니다.",
              "reason": "LLM API 키가 설정되지 않아 규칙기반 점수 결과를 사용했습니다."
            }
            """;

    @Override
    public String complete(String prompt) {
        if (prompt != null && prompt.contains(TASK_QUESTIONS)) {
            return QUESTIONS_JSON;
        }
        return ANALYZE_JSON;
    }

    @Override
    public String providerName() {
        return "mock";
    }
}
