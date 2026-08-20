package com.mcmhouse.catalog;

import com.mcmhouse.domain.House;
import com.mcmhouse.domain.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * products.json 로드 검증. 4개 House 모두 상품이 있어야 추천이 성립한다.
 */
class ProductCatalogTest {

    private ProductCatalog catalog;

    @BeforeEach
    void setUp() throws Exception {
        catalog = new ProductCatalog();
        var load = ProductCatalog.class.getDeclaredMethod("load");
        load.setAccessible(true);
        load.invoke(catalog);
    }

    @Test
    void 모든_House에_상품이_하나_이상_있다() {
        for (House h : House.values()) {
            assertThat(catalog.forHouse(h))
                    .as("%s House 상품", h)
                    .isNotEmpty();
        }
    }

    @Test
    void 전체_상품이_로드된다() {
        assertThat(catalog.all()).hasSizeGreaterThanOrEqualTo(20);
    }

    @Test
    void 상품의_image_필드는_비어있지_않다() {
        // 프론트가 이 값으로 이미지를 찾으므로 비면 안 된다
        assertThat(catalog.all())
                .allSatisfy(p -> assertThat(p.image()).isNotBlank());
    }

    @Test
    void 모든_House의_추천_상품_id가_실제로_존재한다() {
        // 예전에 "MCM-LEG-001" 처럼 카탈로그에 없는 id가 프론트로 나가고 있었다.
        for (House house : House.values()) {
            for (String id : house.getRecommendedProductIds()) {
                assertThat(catalog.findById(id))
                        .as("%s의 추천 상품 %s가 products.json에 없음", house, id)
                        .isNotNull();
            }
        }
    }

    @Test
    void 모든_House의_대표_상품은_그_House_소속이다() {
        // A/B 후보 이미지와 YOUR PICK이 모두 이 값을 쓴다. 다른 House 상품이면 화면이 어긋난다.
        for (House house : House.values()) {
            Product representative = catalog.findById(house.getRepresentativeProductId());
            assertThat(representative).isNotNull();
            assertThat(representative.house()).isEqualTo(house);
        }
    }
}
