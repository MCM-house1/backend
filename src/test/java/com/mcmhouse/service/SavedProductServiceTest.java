package com.mcmhouse.service;

import com.mcmhouse.catalog.ProductCatalog;
import com.mcmhouse.domain.House;
import com.mcmhouse.domain.Product;
import com.mcmhouse.domain.SavedProduct;
import com.mcmhouse.repository.DiagnosisResultRepository;
import com.mcmhouse.repository.SavedProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 상품 저장 서비스 로직 검증. Repository는 Mockito로 대체하고,
 * 저장 상태는 리스트로 흉내 내어 저장/취소/멱등을 확인한다.
 */
class SavedProductServiceTest {

    private SavedProductService service;
    private ProductCatalog products;
    private final List<SavedProduct> store = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        products = new ProductCatalog();
        var load = ProductCatalog.class.getDeclaredMethod("load");
        load.setAccessible(true);
        load.invoke(products);

        store.clear();
        var savedRepo = mock(SavedProductRepository.class);
        var resultRepo = mock(DiagnosisResultRepository.class);

        when(resultRepo.existsById(anyLong())).thenReturn(true);
        when(savedRepo.save(any(SavedProduct.class))).thenAnswer(inv -> {
            store.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(savedRepo.existsByResultIdAndProductId(anyLong(), any())).thenAnswer(inv ->
                store.stream().anyMatch(s -> s.getResultId().equals(inv.getArgument(0))
                        && s.getProductId().equals(inv.getArgument(1))));
        doAnswer(inv -> {
            store.removeIf(s -> s.getResultId().equals(inv.getArgument(0))
                    && s.getProductId().equals(inv.getArgument(1)));
            return null;
        }).when(savedRepo).deleteByResultIdAndProductId(anyLong(), any());
        when(savedRepo.findByResultIdOrderBySavedAtDesc(eq(1L))).thenAnswer(inv ->
                store.stream().filter(s -> s.getResultId().equals(1L)).toList());

        service = new SavedProductService(savedRepo, resultRepo, products);
    }

    @Test
    void 저장하면_목록에_담기고_중복저장은_멱등이다() {
        String pid = products.forHouse(House.LEGACY).get(0).id();

        service.save(1L, pid);
        List<Product> after = service.save(1L, pid); // 중복

        assertThat(after).extracting(Product::id).containsExactly(pid);
    }

    @Test
    void 저장_취소하면_목록에서_빠진다() {
        String pid = products.forHouse(House.LEGACY).get(0).id();
        service.save(1L, pid);

        List<Product> after = service.unsave(1L, pid);

        assertThat(after).isEmpty();
    }

    @Test
    void 없는_상품은_저장할_수_없다() {
        assertThatThrownBy(() -> service.save(1L, "NOPE"))
                .hasMessageContaining("상품을 찾을 수 없습니다");
    }
}
