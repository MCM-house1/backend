package com.mcmhouse.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 점수 계산 · 최종 House 판별 · 추천 순서의 회귀 방지 테스트.
 * (회귀가 나기 쉬운 핵심 로직이라 우선 커버.)
 */
class DiagnosisResultTest {

    private static DiagnosisResult of(House... houses) {
        return new DiagnosisResult(List.of(houses));
    }

    @Test
    void 선택지마다_해당_House에_2점씩_합산된다() {
        var result = of(House.LEGACY, House.LEGACY, House.LEGACY,
                House.LEGACY, House.LEGACY, House.LEGACY);

        assertThat(result.getScoreLegacy()).isEqualTo(12);
        assertThat(result.getScoreInstinct()).isZero();
    }

    @Test
    void 최고점_House_하나면_단일_결과다() {
        var result = of(House.LEGACY, House.LEGACY, House.LEGACY,
                House.CURIOSITY, House.LEGACY, House.LEGACY);

        assertThat(result.getFinalHouses()).containsExactly(House.LEGACY);
        assertThat(result.effectiveHouse()).isEqualTo(House.LEGACY);
    }

    @Test
    void 동점이면_복합형으로_여러_House가_나온다() {
        // LEGACY 3표, CURIOSITY 3표 → 동점
        var result = of(House.LEGACY, House.LEGACY, House.LEGACY,
                House.CURIOSITY, House.CURIOSITY, House.CURIOSITY);

        assertThat(result.getFinalHouses()).containsExactly(House.LEGACY, House.CURIOSITY);
    }

    @Test
    void 추천순서는_점수_내림차순이다() {
        // LEGACY 3, CURIOSITY 2, FREEDOM 1, INSTINCT 0
        var result = of(House.LEGACY, House.LEGACY, House.LEGACY,
                House.CURIOSITY, House.CURIOSITY, House.FREEDOM);

        assertThat(result.recommendedRoute())
                .containsExactly(House.LEGACY, House.CURIOSITY, House.FREEDOM, House.INSTINCT);
    }

    @Test
    void AI가_판별한_House가_있으면_그것을_최종으로_쓴다() {
        var result = of(House.LEGACY, House.LEGACY, House.LEGACY,
                House.LEGACY, House.LEGACY, House.LEGACY);
        // 규칙기반 최고점은 LEGACY지만 AI가 CURIOSITY로 판별
        result.applyAiAnalysis(List.of("답변1", "답변2"), House.CURIOSITY,
                "요약", "근거", false);

        assertThat(result.effectiveHouse()).isEqualTo(House.CURIOSITY);
    }

    @Test
    void 방문하면_스탬프가_기록되고_현재위치가_갱신된다() {
        var result = of(House.LEGACY, House.LEGACY, House.LEGACY,
                House.LEGACY, House.LEGACY, House.LEGACY);

        assertThat(result.hasVisited(House.FREEDOM)).isFalse();
        result.addVisit(new ZoneVisit(House.FREEDOM));
        result.markCurrentZone(House.FREEDOM);

        assertThat(result.hasVisited(House.FREEDOM)).isTrue();
        assertThat(result.getCurrentZone()).isEqualTo(House.FREEDOM);
    }

    @Test
    void 방문한_House의_visitedAt은_기록되고_미방문은_null이다() {
        var result = of(House.LEGACY, House.LEGACY, House.LEGACY,
                House.LEGACY, House.LEGACY, House.LEGACY);

        assertThat(result.visitedAt(House.FREEDOM)).isNull();
        result.addVisit(new ZoneVisit(House.FREEDOM));

        assertThat(result.visitedAt(House.FREEDOM)).isNotNull();
        assertThat(result.visitedAt(House.CURIOSITY)).isNull();
    }
}
