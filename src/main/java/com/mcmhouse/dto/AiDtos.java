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
}
