package com.mcmhouse.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** AI 아이덴티티 분석 엔드포인트의 요청/응답 DTO. */
public final class AiDtos {

    private AiDtos() {}

    /** 후속질문 생성 응답. */
    public record AiQuestionsView(
            Long resultId,
            List<String> questions,
            boolean fallback        // true면 LLM 실패로 기본 질문을 사용
    ) {}

    /** 후속질문에 대한 답변 제출. 질문 개수와 같은 수의 답변이 필요하다. */
    public record AiAnalyzeRequest(
            @NotNull(message = "answers는 필수입니다.")
            @Size(min = 1, max = 5, message = "답변은 1~5개여야 합니다.")
            List<@NotNull String> answers
    ) {}

    /* ---------- A/B 이미지 선택 ---------- */

    /** A/B 후보 House 1개(이미지 포함). */
    public record StyleChoiceOption(String house, String title, String image) {}

    /**
     * A/B 이미지 선택 화면 조회 응답. 6문항 점수 1·2등 House를 후보로 내려준다.
     * 이미지는 placeholder 경로이며 확정 이미지가 오면 House 쪽 값만 교체하면 된다.
     */
    public record StyleChoiceOptionsView(
            Long resultId,
            StyleChoiceOption optionA,
            StyleChoiceOption optionB
    ) {}

    /** A/B 선택 제출. reason은 선택(생략 가능) — 없으면 LLM 호출 없이 선택한 House를 그대로 최종으로 채택한다. */
    public record StyleChoiceRequest(
            @NotNull(message = "chosenHouse는 필수입니다.")
            String chosenHouse,
            String reason
    ) {}
}
