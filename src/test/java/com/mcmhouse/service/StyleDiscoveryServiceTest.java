package com.mcmhouse.service;

import com.mcmhouse.catalog.ProductCatalog;
import com.mcmhouse.domain.House;
import com.mcmhouse.domain.StyleDiscovery;
import com.mcmhouse.dto.StyleDiscoveryDtos.StyleDiscoveryRequest;
import com.mcmhouse.dto.StyleDiscoveryDtos.StyleDiscoveryView;
import com.mcmhouse.llm.LlmClient;
import com.mcmhouse.llm.LlmException;
import com.mcmhouse.repository.DiagnosisResultRepository;
import com.mcmhouse.repository.StyleDiscoveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 셀카 무드 분석 검증.
 * selectedProductId는 선택이며, 없으면 AI가 셀카에서 해당 House 상품 중 하나를 특정한다.
 * 매치 후보는 House로 좁히지 않은 전체 상품이다.
 * 비전이 실패해도(또는 이미지가 없어도) 폴백으로 안전하게 결과가 채워지는지 본다.
 */
class StyleDiscoveryServiceTest {

    private StyleDiscoveryService service;
    private ProductCatalog products;

    /** 비전 호출이 항상 실패하는 LLM (폴백 강제). */
    private static final LlmClient FAILING_LLM = new LlmClient() {
        @Override public String complete(String prompt) { throw new LlmException("실패"); }
        @Override public String completeWithImage(String p, String b, String m) { throw new LlmException("비전 실패"); }
        @Override public String providerName() { return "failing"; }
    };

    /** 사진에서 지정한 상품을 골라내는 비전 LLM 스텁. */
    private static LlmClient visionPicking(String pickedId) {
        String json = """
                {"pickedProductId":"%s","styleTitle":"차분한 대비",
                 "styleDescription":"군더더기 없는 선을 좋아하시는 편이에요.",
                 "keywords":["정돈된","도시적인","존재감 있는"],
                 "impression":"단정하면서도 시선이 머무는 인상이에요.",
                 "matches":[{"id":"03_REC1","reason":"결이 비슷해요."},
                            {"id":"04_REC1","reason":"함께 두면 균형이 좋아요."}]}
                """.formatted(pickedId);
        return new LlmClient() {
            @Override public String complete(String prompt) { return json; }
            @Override public String completeWithImage(String p, String b, String m) { return json; }
            @Override public String providerName() { return "stub"; }
        };
    }

    private DiagnosisResultRepository resultRepo;
    private StyleDiscoveryRepository discoveryRepo;

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

    @Test
    void 비전_실패시_폴백으로_스타일과_매칭상품이_채워진다() {
        var req = new StyleDiscoveryRequest("data:image/jpeg;base64,AAAA", "LEGACY");

        StyleDiscoveryView view = service.analyze(1L, req);

        assertThat(view.fallback()).isTrue();
        assertThat(view.styleTitle()).isNotBlank();
        assertThat(view.styleKeywords()).isNotEmpty();
        assertThat(view.completeTheLook()).hasSize(2);
        assertThat(view.yourPick()).isNull();          // 비전이 죽으면 상품을 특정할 수 없다
    }

    @Test
    void 이미지가_없으면_폴백으로_동작하고_상품은_비운다() {
        var req = new StyleDiscoveryRequest(null, "CURIOSITY");

        StyleDiscoveryView view = service.analyze(1L, req);

        assertThat(view.fallback()).isTrue();
        assertThat(view.house()).isEqualTo("CURIOSITY");
        assertThat(view.yourPick()).isNull();
        assertThat(view.completeTheLook()).isNotEmpty();
    }

    @Test
    void AI가_셀카에서_상품을_찾아낸다() {
        var service = serviceWith(visionPicking("01_REC3"));
        var req = new StyleDiscoveryRequest("data:image/jpeg;base64,AAAA", "LEGACY");

        StyleDiscoveryView view = service.analyze(1L, req);

        assertThat(view.fallback()).isFalse();
        assertThat(view.yourPick().id()).isEqualTo("01_REC3");
    }

    @Test
    void AI가_다른_House_상품을_고르면_인정하지_않는다() {
        // LEGACY 미션인데 INSTINCT 상품(02_REC1)을 골라온 경우
        var service = serviceWith(visionPicking("02_REC1"));
        var req = new StyleDiscoveryRequest("data:image/jpeg;base64,AAAA", "LEGACY");

        StyleDiscoveryView view = service.analyze(1L, req);

        assertThat(view.yourPick()).isNull();
    }

    @Test
    void AI가_없는_상품_id를_지어내면_인정하지_않는다() {
        var service = serviceWith(visionPicking("존재하지않는id"));
        var req = new StyleDiscoveryRequest("data:image/jpeg;base64,AAAA", "LEGACY");

        StyleDiscoveryView view = service.analyze(1L, req);

        assertThat(view.yourPick()).isNull();
        assertThat(view.completeTheLook()).isNotEmpty();   // 스타일 분석 자체는 살아있다
    }

    @Test
    void 찾아낸_상품은_매치_목록에_다시_넣지_않는다() {
        // 스텁이 matches에 03_REC1, 04_REC1을 주고 pick은 01_REC3이므로 겹치지 않아야 한다
        var service = serviceWith(visionPicking("01_REC3"));
        var req = new StyleDiscoveryRequest("data:image/jpeg;base64,AAAA", "LEGACY");

        StyleDiscoveryView view = service.analyze(1L, req);

        assertThat(view.completeTheLook()).allSatisfy(m ->
                assertThat(m.product().id()).isNotEqualTo(view.yourPick().id()));
    }

    @Test
    void 알_수_없는_House는_거부한다() {
        var req = new StyleDiscoveryRequest("data:image/jpeg;base64,AAAA", "NOPE");

        assertThatThrownBy(() -> service.analyze(1L, req))
                .hasMessageContaining("알 수 없는 House");
    }

    @Test
    void 매치_후보는_House로_좁히지_않은_전체_상품이다() {
        // 찾아낸 상품은 LEGACY지만, 매치는 다른 House에서도 고를 수 있어야 한다.
        var service = serviceWith(visionPicking("01_REC3"));
        var req = new StyleDiscoveryRequest("data:image/jpeg;base64,AAAA", "LEGACY");

        StyleDiscoveryView view = service.analyze(1L, req);

        // 스텁이 고른 03_REC1(FREEDOM), 04_REC1(CURIOSITY)이 그대로 살아남는다
        assertThat(view.completeTheLook()).extracting(m -> m.product().house().name())
                .contains("FREEDOM", "CURIOSITY");
    }
}
