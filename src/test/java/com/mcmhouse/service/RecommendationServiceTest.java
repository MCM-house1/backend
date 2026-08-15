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
        // 매칭 상품은 자기 자신을 제외한 같은 House 상품이어야 한다
        assertThat(view.completeTheLook())
                .allSatisfy(m -> {
                    assertThat(m.product().house()).isEqualTo(House.LEGACY);
                    assertThat(m.product().id()).isNotEqualTo(product.id());
                    assertThat(m.reason()).isNotBlank();
                });
    }
}
