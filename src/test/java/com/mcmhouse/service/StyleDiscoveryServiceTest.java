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
 * 셀카 무드 분석 검증. 비전이 실패해도(또는 이미지가 없어도) 폴백으로 안전하게 결과가 채워지는지 본다.
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

    @BeforeEach
    void setUp() throws Exception {
        products = new ProductCatalog();
        var load = ProductCatalog.class.getDeclaredMethod("load");
        load.setAccessible(true);
        load.invoke(products);

        var resultRepo = mock(DiagnosisResultRepository.class);
        when(resultRepo.existsById(anyLong())).thenReturn(true);

        var discoveryRepo = mock(StyleDiscoveryRepository.class);
        when(discoveryRepo.findByResultIdAndHouse(anyLong(), any())).thenReturn(Optional.empty());
        when(discoveryRepo.save(any(StyleDiscovery.class))).thenAnswer(inv -> inv.getArgument(0));

        service = new StyleDiscoveryService(FAILING_LLM, products, resultRepo, discoveryRepo);
    }

    @Test
    void 비전_실패시_폴백으로_스타일과_매칭상품이_채워진다() {
        var req = new StyleDiscoveryRequest("data:image/jpeg;base64,AAAA", "LEGACY", "01_REC1");

        StyleDiscoveryView view = service.analyze(1L, req);

        assertThat(view.fallback()).isTrue();
        assertThat(view.styleTitle()).isNotBlank();
        assertThat(view.styleKeywords()).isNotEmpty();
        assertThat(view.completeTheLook()).hasSize(2);
        assertThat(view.yourPick().id()).isEqualTo("01_REC1");
    }

    @Test
    void 이미지가_없어도_폴백으로_동작한다() {
        var req = new StyleDiscoveryRequest(null, "CURIOSITY", null);

        StyleDiscoveryView view = service.analyze(1L, req);

        assertThat(view.fallback()).isTrue();
        assertThat(view.house()).isEqualTo("CURIOSITY");
        assertThat(view.yourPick()).isNull();
    }

    @Test
    void 알_수_없는_House는_거부한다() {
        var req = new StyleDiscoveryRequest("data:image/jpeg;base64,AAAA", "NOPE", null);

        assertThatThrownBy(() -> service.analyze(1L, req))
                .hasMessageContaining("알 수 없는 House");
    }
}
