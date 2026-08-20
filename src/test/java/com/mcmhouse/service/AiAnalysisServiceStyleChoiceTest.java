package com.mcmhouse.service;

import com.mcmhouse.catalog.QuestionCatalog;
import com.mcmhouse.domain.DiagnosisResult;
import com.mcmhouse.domain.House;
import com.mcmhouse.llm.LlmClient;
import com.mcmhouse.llm.LlmException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A/B 이미지 선택(스타일 초이스) 판별 로직 검증.
 *
 * <p>핵심은 <b>6문항 점수와 이미지 선택이 함께 반영되는가</b>다. 예전에는 고른 House가 무조건
 * 최종이 되어 문항 6개가 탭 한 번에 덮였다. 지금은 선택에 가산점(3점)을 얹어 합산하므로
 * 한 문항 차이(2점)까지만 뒤집히고, 두 문항 이상 벌어지면 문항 결과가 유지된다.
 *
 * <p>LLM은 호출하지 않는다(사유 입력이 없어졌다). 그래서 항상 실패하는 LLM을 물려도 동작해야 한다.
 */
class AiAnalysisServiceStyleChoiceTest {

    private static final LlmClient FAILING_LLM = new LlmClient() {
        @Override public String complete(String prompt) { throw new LlmException("실패"); }
        @Override public String providerName() { return "failing"; }
    };

    private final AiAnalysisService service = new AiAnalysisService(FAILING_LLM, new QuestionCatalog());

    private DiagnosisResult resultWith(House... answers) {
        return new DiagnosisResult(List.of(answers));
    }

    @Test
    void 일이등_House를_반환한다() {
        // LEGACY 3표(6점), CURIOSITY 2표(4점), FREEDOM 1표(2점)
        var result = resultWith(House.LEGACY, House.LEGACY, House.LEGACY,
                House.CURIOSITY, House.CURIOSITY, House.FREEDOM);

        assertThat(service.topTwoHouses(result)).containsExactly(House.LEGACY, House.CURIOSITY);
    }

    @Test
    void 한_문항_차이면_이미지_선택이_뒤집는다() {
        // LEGACY 6점 vs CURIOSITY 4점 → 2점 차. CURIOSITY 선택 시 4+3=7 > 6
        var result = resultWith(House.LEGACY, House.LEGACY, House.LEGACY,
                House.CURIOSITY, House.CURIOSITY, House.FREEDOM);

        var analysis = service.analyzeStyleChoice(result, House.CURIOSITY);

        assertThat(analysis.house()).isEqualTo(House.CURIOSITY);
        assertThat(analysis.fallback()).isFalse();
    }

    @Test
    void 두_문항_이상_차이나면_육문항_결과가_유지된다() {
        // LEGACY 8점 vs CURIOSITY 4점 → 4점 차. CURIOSITY 선택해도 4+3=7 < 8
        var result = resultWith(House.LEGACY, House.LEGACY, House.LEGACY, House.LEGACY,
                House.CURIOSITY, House.CURIOSITY);

        var analysis = service.analyzeStyleChoice(result, House.CURIOSITY);

        assertThat(analysis.house()).isEqualTo(House.LEGACY);
        assertThat(analysis.reason()).contains("6문항");   // 왜 선택과 다른지 설명해야 한다
    }

    @Test
    void 일등을_고르면_그대로_확정된다() {
        var result = resultWith(House.LEGACY, House.LEGACY, House.LEGACY,
                House.CURIOSITY, House.CURIOSITY, House.FREEDOM);

        assertThat(service.analyzeStyleChoice(result, House.LEGACY).house()).isEqualTo(House.LEGACY);
    }

    @Test
    void 동점이면_고른_쪽이_결정된다() {
        // LEGACY 6점, CURIOSITY 6점 동점 → 선택이 결정타
        var result = resultWith(House.LEGACY, House.LEGACY, House.LEGACY,
                House.CURIOSITY, House.CURIOSITY, House.CURIOSITY);

        assertThat(service.analyzeStyleChoice(result, House.CURIOSITY).house()).isEqualTo(House.CURIOSITY);
    }

    @Test
    void 일이등이_아닌_House를_선택하면_거부한다() {
        var result = resultWith(House.LEGACY, House.LEGACY, House.LEGACY,
                House.CURIOSITY, House.CURIOSITY, House.FREEDOM);

        assertThatThrownBy(() -> service.analyzeStyleChoice(result, House.FREEDOM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1·2등");
    }

    @Test
    void 최종_점수에_선택_가산점이_반영된다() {
        // 화면 막대가 결과와 어긋나 보이지 않으려면 finalScoreMap에 가산점이 들어가야 한다
        var result = resultWith(House.LEGACY, House.LEGACY, House.LEGACY,
                House.CURIOSITY, House.CURIOSITY, House.FREEDOM);

        assertThat(result.finalScoreMap()).containsEntry(House.CURIOSITY, 4);   // 선택 전
        result.applyStyleChoice(House.CURIOSITY);

        assertThat(result.finalScoreMap())
                .containsEntry(House.CURIOSITY, 4 + DiagnosisResult.STYLE_CHOICE_BONUS)
                .containsEntry(House.LEGACY, 6);
        assertThat(result.scoreMap()).containsEntry(House.CURIOSITY, 4);        // 원점수는 그대로
    }
}
