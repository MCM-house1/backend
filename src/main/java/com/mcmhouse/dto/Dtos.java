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

    public record ResultView(
            Long resultId,
            Map<String, Integer> scores,
            List<String> finalHouses,   // 1개=단일, 2개+=복합형
            boolean combo,
            HouseView primaryHouse,
            List<String> recommendedRoute
    ) {
        public static ResultView from(DiagnosisResult r) {
            Map<String, Integer> scores = r.scoreMap().entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(e -> e.getKey().name(),
                            Map.Entry::getValue, (a, b) -> a, java.util.LinkedHashMap::new));
            List<String> finals = r.getFinalHouses().stream().map(Enum::name).toList();
            List<String> route = r.recommendedRoute().stream().map(Enum::name).toList();
            return new ResultView(
                    r.getId(), scores, finals, finals.size() >= 2,
                    HouseView.of(r.getFinalHouses().get(0)), route
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
            List<ZoneStatusView> zones      // 추천 순서대로 정렬된 전체 Zone 현황
    ) {}
}
