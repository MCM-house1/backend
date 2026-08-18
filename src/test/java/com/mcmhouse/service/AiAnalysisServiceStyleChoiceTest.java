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
 * 사유 입력이 없는 방식이라 LLM을 호출하지 않고 항상 즉시 확정된다.
 */
class AiAnalysisServiceStyleChoiceTest {

    private static final LlmClient FAILING_LLM = new LlmClient() {
        @Override public String complete(String prompt) { throw new LlmException("실패"); }
        @Override public String providerName() { return "failing"; }
    };

    private DiagnosisResult resultWith(House... answers) {
        return new DiagnosisResult(List.of(answers));
    }

    @Test
    void 일이등_House를_반환한다() {
        var service = new AiAnalysisService(FAILING_LLM, new QuestionCatalog());
        // LEGACY 3표, CURIOSITY 2표, FREEDOM 1표 → 1등 LEGACY, 2등 CURIOSITY
        var result = resultWith(House.LEGACY, House.LEGACY, House.LEGACY,
                House.CURIOSITY, House.CURIOSITY, House.FREEDOM);

        List<House> topTwo = service.topTwoHouses(result);

        assertThat(topTwo).containsExactly(House.LEGACY, House.CURIOSITY);
    }

    @Test
    void 선택하면_LLM_호출없이_그_House로_즉시_확정된다() {
        var service = new AiAnalysisService(FAILING_LLM, new QuestionCatalog());
        var result = resultWith(House.LEGACY, House.LEGACY, House.LEGACY,
                House.CURIOSITY, House.CURIOSITY, House.FREEDOM);

        var analysis = service.analyzeStyleChoice(result, House.CURIOSITY);

        assertThat(analysis.house()).isEqualTo(House.CURIOSITY);
        assertThat(analysis.fallback()).isFalse();
    }

    @Test
    void 일이등이_아닌_House를_선택하면_거부한다() {
        var service = new AiAnalysisService(FAILING_LLM, new QuestionCatalog());
        var result = resultWith(House.LEGACY, House.LEGACY, House.LEGACY,
                House.CURIOSITY, House.CURIOSITY, House.FREEDOM);

        assertThatThrownBy(() -> service.analyzeStyleChoice(result, House.FREEDOM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1·2등");
    }
}
