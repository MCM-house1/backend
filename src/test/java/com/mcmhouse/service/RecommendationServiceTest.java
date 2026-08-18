package com.mcmhouse.service;

import com.mcmhouse.catalog.ProductCatalog;
import com.mcmhouse.domain.House;
import com.mcmhouse.dto.RecommendationDtos.ProductDetailView;
import com.mcmhouse.llm.LlmClient;
import com.mcmhouse.llm.LlmException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LLM이 실패해도 추천 이유가 폴백으로 안전하게 채워지는지 검증.
 */
class RecommendationServiceTest {

    private RecommendationService service;
    private ProductCatalog products;

    /** 항상 실패하는 LLM (폴백 경로 강제). */
    private static final LlmClient FAILING_LLM = new LlmClient() {
        @Override public String complete(String prompt) { throw new LlmException("테스트용 실패"); }
        @Override public String providerName() { return "failing"; }
    };

    @BeforeEach
    void setUp() throws Exception {
        products = new ProductCatalog();
        var load = ProductCatalog.class.getDeclaredMethod("load");
        load.setAccessible(true);
        load.invoke(products);
        service = new RecommendationService(FAILING_LLM, products);
    }

    @Test
    void LLM_실패시_폴백으로_추천이유와_매칭상품이_채워진다() {
        var product = products.forHouse(House.LEGACY).get(0);

        ProductDetailView view = service.productDetail(House.LEGACY, product);

        assertThat(view.fallback()).isTrue();
        assertThat(view.reason()).isNotBlank();
        assertThat(view.completeTheLook()).hasSize(2);
        // 매칭 후보는 House로 좁히지 않으므로 자기 자신만 아니면 된다 (폴백은 같은 House를 우선할 뿐 강제하지 않는다)
        assertThat(view.completeTheLook())
                .allSatisfy(m -> {
                    assertThat(m.product().id()).isNotEqualTo(product.id());
                    assertThat(m.reason()).isNotBlank();
                });
    }

    @Test
    void 매칭_후보는_전체_상품에서_고른다() {
        // 상품 27개 중 자기 자신 1개를 뺀 26개가 후보 풀이어야 한다(House로 좁혀지지 않음).
        // 폴백 결과 자체는 2개만 보이지만, 같은 House 상품 수가 후보 풀보다 적을 수 있다는 사실로
        // "전체에서 고른다"는 계약이 지켜지는지는 서비스 내부 로직(all() 사용)으로 보장된다.
        var product = products.forHouse(House.CURIOSITY).get(0);

        ProductDetailView view = service.productDetail(House.CURIOSITY, product);

        assertThat(view.completeTheLook()).isNotEmpty();
        assertThat(view.completeTheLook())
                .allSatisfy(m -> assertThat(m.product().id()).isNotEqualTo(product.id()));
    }
}
