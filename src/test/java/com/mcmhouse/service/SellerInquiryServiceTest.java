package com.mcmhouse.service;

import com.mcmhouse.catalog.ProductCatalog;
import com.mcmhouse.domain.House;
import com.mcmhouse.domain.SellerInquiry;
import com.mcmhouse.repository.DiagnosisResultRepository;
import com.mcmhouse.repository.SellerInquiryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 셀러 상담 요청 서비스 검증. Repository는 Mockito로 대체한다.
 */
class SellerInquiryServiceTest {

    private SellerInquiryService service;
    private ProductCatalog products;
    private final List<SellerInquiry> store = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        products = new ProductCatalog();
        var load = ProductCatalog.class.getDeclaredMethod("load");
        load.setAccessible(true);
        load.invoke(products);

        store.clear();
        var inquiryRepo = mock(SellerInquiryRepository.class);
        var resultRepo = mock(DiagnosisResultRepository.class);

        when(resultRepo.existsById(anyLong())).thenReturn(true);
        when(inquiryRepo.save(any(SellerInquiry.class))).thenAnswer(inv -> {
            store.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(inquiryRepo.findByResultIdOrderByRequestedAtDesc(eq(1L))).thenAnswer(inv ->
                store.stream().filter(s -> s.getResultId().equals(1L)).toList());

        service = new SellerInquiryService(inquiryRepo, resultRepo, products);
    }

    @Test
    void 상담을_요청하면_상품과_함께_기록되고_완료된다() {
        String pid = products.forHouse(House.LEGACY).get(0).id();

        var result = service.request(1L, pid);

        assertThat(result.product().id()).isEqualTo(pid);
        assertThat(service.list(1L)).hasSize(1);
    }

    @Test
    void 없는_상품에는_상담을_요청할_수_없다() {
        assertThatThrownBy(() -> service.request(1L, "NOPE"))
                .hasMessageContaining("상품을 찾을 수 없습니다");
    }
}
