package com.mcmhouse.dto;

import com.mcmhouse.domain.DiagnosisResult;
import com.mcmhouse.domain.House;
import com.mcmhouse.domain.QuestionCatalog;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/** 요청/응답 DTO 모음. */
public final class Dtos {

    private Dtos() {}

    /* ---------- 질문 조회 ---------- */
    public record OptionView(int index, String text) {}
    public record QuestionView(int no, String text, List<OptionView> options) {}

    public static QuestionView toQuestionView(QuestionCatalog.Question q) {
        List<OptionView> opts = q.options().stream()
                .map(o -> new OptionView(o.index(), o.text()))
                .toList();
        return new QuestionView(q.no(), q.text(), opts);
    }

    /* ---------- 진단 제출 ----------
       answers[i] = i+1번 질문에서 고른 선택지 index(0~3) */
    public record SubmitRequest(
            @NotNull @Size(min = 6, max = 6, message = "6개 질문 모두 답변해야 합니다.")
            List<@NotNull Integer> answers
    ) {}

    /* ---------- 진단 결과 ---------- */
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

    /* ---------- Zone 방문 인증 ---------- */
    public record VisitRequest(
            @NotNull(message = "QR/NFC 스캔값(scanValue) 또는 house 중 하나는 필요합니다.")
            String scanValue    // 예: "LEGACY", "ZONE:LEGACY", "https://.../legacy"
    ) {}

    public record ZoneStatusView(
            String house, String zoneName, String zoneMission,
            String color, int order, boolean visited
    ) {}

    public record PassportView(
            Long resultId,
            int visitedCount,
            int totalZones,
            boolean completed,
            String nextRecommended,         // 다음 추천 미방문 Zone (없으면 null)
            String currentZone,             // 마지막으로 스캔한 Zone (없으면 null)
            List<ZoneStatusView> zones      // 추천 순서대로 정렬된 전체 Zone 현황
    ) {}

    /* ---------- 현재 위치 ----------
       GPS가 아니라 마지막으로 스캔한 Zone을 돌려주는 것뿐이다. */
    public record CurrentZoneView(
            Long resultId,
            String currentZone,     // 아직 아무 Zone도 스캔하지 않았으면 null
            String zoneName,
            String zoneMission,
            String color,
            boolean visited,        // 해당 Zone의 체험(방문 인증)이 끝났는지
            String nextRecommended  // 미방문 중 추천 순위가 가장 높은 Zone
    ) {}

    /* ---------- AI 아이덴티티 분석 ---------- */

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
