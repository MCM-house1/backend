package com.mcmhouse.dto;

import com.mcmhouse.domain.DiagnosisResult;
import com.mcmhouse.domain.House;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/** 진단 제출·결과 조회 관련 DTO (AI 분석 결과 포함). */
public final class ResultDtos {

    private ResultDtos() {}

    /** answers[i] = i+1번 질문에서 고른 선택지 index(0~3). */
    public record SubmitRequest(
            @NotNull @Size(min = 6, max = 6, message = "6개 질문 모두 답변해야 합니다.")
            List<@NotNull Integer> answers
    ) {}

    public record HouseView(
            String key, String title, String description,
            List<String> tags, String zoneName, String color,
            List<String> recommendedProductIds
    ) {
        public static HouseView of(House h) {
            return new HouseView(h.name(), h.getTitle(), h.getDescription(),
                    h.getTags(), h.getZoneName(), h.getColor(), h.getRecommendedProductIds());
        }
    }

    /** AI 아이덴티티 분석 결과. 아직 분석 전이면 analyzed=false이고 나머지는 null/빈 값. */
    public record AiAnalysisView(
            boolean analyzed,
            boolean fallback,           // true면 LLM 실패로 규칙기반 점수 결과를 사용
            List<String> questions,     // LLM이 던진 자연어 후속질문
            List<String> answers,       // 사용자의 답변
            String house,               // LLM이 판별한 House (폴백이면 null)
            String summary,
            String reason
    ) {
        public static AiAnalysisView from(DiagnosisResult r) {
            return new AiAnalysisView(
                    r.isAiAnalyzed(),
                    r.isAiFallback(),
                    r.getAiQuestions(),
                    r.getAiAnswers(),
                    r.getAiHouse() == null ? null : r.getAiHouse().name(),
                    r.getAiSummary(),
                    r.getAiReason()
            );
        }
    }

    public record ResultView(
            Long resultId,
            Map<String, Integer> scores,
            List<String> finalHouses,   // 1개=단일, 2개+=복합형
            boolean combo,
            String comboTitle,          // 예: "LEGACY × CURIOSITY" (단일이면 House title)
            String comboDescription,    // 복합형 한 줄 설명 (단일이면 House description)
            HouseView primaryHouse,     // 화면에 크게 보여줄 대표 House
            List<String> recommendedRoute,
            AiAnalysisView ai
    ) {
        public static ResultView from(DiagnosisResult r) {
            Map<String, Integer> scores = r.scoreMap().entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(e -> e.getKey().name(),
                            Map.Entry::getValue, (a, b) -> a, java.util.LinkedHashMap::new));
            List<House> finalHouses = r.getFinalHouses();
            List<String> finals = finalHouses.stream().map(Enum::name).toList();
            List<String> route = r.recommendedRoute().stream().map(Enum::name).toList();

            // AI 판별이 있으면 그것을 대표 House로, 없으면 규칙기반 최고점 House를 쓴다.
            House primary = r.effectiveHouse();

            return new ResultView(
                    r.getId(), scores, finals, finals.size() >= 2,
                    House.comboTitle(finalHouses),
                    House.comboDescription(finalHouses),
                    HouseView.of(primary), route,
                    AiAnalysisView.from(r)
            );
        }
    }
}
