package com.mcmhouse.service;

import com.mcmhouse.catalog.ProductCatalog;
import com.mcmhouse.domain.StyleDiscovery;
import com.mcmhouse.dto.StyleDiscoveryDtos.StyleDiscoveryRequest;
import com.mcmhouse.dto.StyleDiscoveryDtos.StyleDiscoveryView;
import com.mcmhouse.llm.LlmClient;
import com.mcmhouse.llm.LlmException;
import com.mcmhouse.repository.DiagnosisResultRepository;
import com.mcmhouse.repository.StyleDiscoveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 셀카 무드 분석 검증.
 *
 * <p>YOUR PICK 상품은 House별 고정 매핑이라 비전이 죽든 이미지가 없든 항상 채워져야 한다.
 * 화면의 상품 카드가 비면 안 되기 때문이다.
 * 스타일 문장과 매치 상품은 비전이 만들고, 실패 시 폴백으로 채워진다.
 */
class StyleDiscoveryServiceTest {

    private StyleDiscoveryService service;
    private ProductCatalog products;
    private DiagnosisResultRepository resultRepo;
    private StyleDiscoveryRepository discoveryRepo;

    /** 비전 호출이 항상 실패하는 LLM (폴백 강제). */
    private static final LlmClient FAILING_LLM = new LlmClient() {
        @Override public String complete(String prompt) { throw new LlmException("실패"); }
        @Override public String completeWithImage(String p, String b, String m) { throw new LlmException("비전 실패"); }
        @Override public String providerName() { return "failing"; }
    };

    /** 정상 응답을 주는 비전 LLM 스텁. 매치로 FREEDOM·CURIOSITY 상품을 고른다. */
    private static final LlmClient VISION_LLM = new LlmClient() {
        private static final String JSON = """
                {"styleTitle":"차분한 대비",
                 "styleDescription":"군더더기 없는 선을 좋아하시는 편이에요.",
                 "keywords":["정돈된","도시적인","존재감 있는"],
                 "impression":"단정하면서도 시선이 머무는 인상이에요.",
                 "matches":[{"id":"03_REC1","reason":"결이 비슷해요."},
                            {"id":"04_REC1","reason":"함께 두면 균형이 좋아요."}]}
                """;
        @Override public String complete(String prompt) { return JSON; }
        @Override public String completeWithImage(String p, String b, String m) { return JSON; }
        @Override public String providerName() { return "stub"; }
    };

    @BeforeEach
    void setUp() throws Exception {
        products = new ProductCatalog();
        var load = ProductCatalog.class.getDeclaredMethod("load");
        load.setAccessible(true);
        load.invoke(products);

        resultRepo = mock(DiagnosisResultRepository.class);
        when(resultRepo.existsById(anyLong())).thenReturn(true);

        discoveryRepo = mock(StyleDiscoveryRepository.class);
        when(discoveryRepo.findByResultIdAndHouse(anyLong(), any())).thenReturn(Optional.empty());
        when(discoveryRepo.save(any(StyleDiscovery.class))).thenAnswer(inv -> inv.getArgument(0));

        service = serviceWith(FAILING_LLM);
    }

    /** 같은 카탈로그/저장소에 LLM만 바꿔 끼운 서비스. */
    private StyleDiscoveryService serviceWith(LlmClient llm) {
        return new StyleDiscoveryService(llm, products, resultRepo, discoveryRepo);
    }

    @ParameterizedTest
    @CsvSource({"LEGACY,01_REC3", "INSTINCT,02_REC2", "FREEDOM,03_REC1", "CURIOSITY,04_REC1"})
    void House마다_고정된_상품이_YOUR_PICK으로_내려온다(String house, String expectedId) {
        var view = serviceWith(VISION_LLM)
                .analyze(1L, new StyleDiscoveryRequest("data:image/jpeg;base64,AAAA", house));

        assertThat(view.yourPick().id()).isEqualTo(expectedId);
        assertThat(view.fallback()).isFalse();
    }

    @Test
    void 비전이_실패해도_YOUR_PICK은_비지_않는다() {
        var req = new StyleDiscoveryRequest("data:image/jpeg;base64,AAAA", "LEGACY");

        StyleDiscoveryView view = service.analyze(1L, req);

        assertThat(view.fallback()).isTrue();
        assertThat(view.yourPick().id()).isEqualTo("01_REC3");   // 화면 카드가 비면 안 된다
        assertThat(view.styleTitle()).isNotBlank();
        assertThat(view.styleKeywords()).isNotEmpty();
        assertThat(view.completeTheLook()).hasSize(2);
    }

    @Test
    void 이미지가_없어도_YOUR_PICK은_비지_않는다() {
        var req = new StyleDiscoveryRequest(null, "CURIOSITY");

        StyleDiscoveryView view = service.analyze(1L, req);

        assertThat(view.fallback()).isTrue();
        assertThat(view.house()).isEqualTo("CURIOSITY");
        assertThat(view.yourPick().id()).isEqualTo("04_REC1");
        assertThat(view.completeTheLook()).isNotEmpty();
    }

    @Test
    void 기준_상품은_매치_목록에_다시_들어가지_않는다() {
        var view = serviceWith(VISION_LLM)
                .analyze(1L, new StyleDiscoveryRequest("data:image/jpeg;base64,AAAA", "FREEDOM"));

        // FREEDOM의 고정 상품은 03_REC1인데 스텁이 매치로도 03_REC1을 준다
        assertThat(view.yourPick().id()).isEqualTo("03_REC1");
        assertThat(view.completeTheLook()).allSatisfy(m ->
                assertThat(m.product().id()).isNotEqualTo("03_REC1"));
    }

    @Test
    void 매치_후보는_House로_좁히지_않은_전체_상품이다() {
        // 기준 상품은 LEGACY지만 매치는 다른 House에서도 고를 수 있어야 한다.
        var view = serviceWith(VISION_LLM)
                .analyze(1L, new StyleDiscoveryRequest("data:image/jpeg;base64,AAAA", "LEGACY"));

        assertThat(view.completeTheLook()).extracting(m -> m.product().house().name())
                .contains("FREEDOM", "CURIOSITY");
    }

    @Test
    void 알_수_없는_House는_거부한다() {
        var req = new StyleDiscoveryRequest("data:image/jpeg;base64,AAAA", "NOPE");

        assertThatThrownBy(() -> service.analyze(1L, req))
                .hasMessageContaining("알 수 없는 House");
    }
}
