package com.mcmhouse.catalog;

import com.mcmhouse.domain.House;
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
}
