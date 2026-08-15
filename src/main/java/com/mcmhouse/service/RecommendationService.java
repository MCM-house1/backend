package com.mcmhouse.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcmhouse.catalog.ProductCatalog;
import com.mcmhouse.domain.House;
import com.mcmhouse.domain.Product;
import com.mcmhouse.dto.RecommendationDtos.MatchItem;
import com.mcmhouse.dto.RecommendationDtos.ProductDetailView;
import com.mcmhouse.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 상품 추천 이유 생성.
 *
 * <p>진단으로 나온 House와 상품 정보를 근거로 LLM이 "왜 이 제품이 당신에게 맞는지"와
 * 함께 매치할 상품(COMPLETE THE LOOK)을 생성한다.
 *
 * <p>LLM 호출이 실패하면 예외를 던지지 않고 기본 문구 + 같은 House의 다른 상품으로 폴백한다.
 */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    private static final int MATCH_COUNT = 2;

    private final LlmClient llm;
    private final ProductCatalog products;
    private final ObjectMapper mapper = new ObjectMapper();

    public RecommendationService(LlmClient llm, ProductCatalog products) {
        this.llm = llm;
        this.products = products;
    }

    /** 상품 상세: 추천 이유 + 함께 매치할 상품(이유 포함)을 생성한다. */
    public ProductDetailView productDetail(House house, Product product) {
        List<Product> candidates = products.forHouse(house).stream()
                .filter(p -> !p.id().equals(product.id()))
                .toList();

        try {
            JsonNode root = mapper.readTree(stripCodeFence(llm.complete(buildPrompt(house, product, candidates))));
            String reason = root.path("reason").asText("").trim();
            String story = root.path("story").asText("").trim();

            List<MatchItem> matches = new ArrayList<>();
            for (JsonNode m : root.path("matches")) {
                Product matched = products.findById(m.path("id").asText(""));
                if (matched != null && matches.size() < MATCH_COUNT) {
                    matches.add(new MatchItem(matched, m.path("reason").asText("").trim()));
                }
            }
            if (reason.isBlank() || matches.isEmpty()) {
                return fallback(house, product, candidates);
            }
            return new ProductDetailView(house.name(), product, reason,
                    story.isBlank() ? null : story, false, matches);

        } catch (Exception e) {
            log.warn("추천 이유 생성 실패 → 폴백. product={}, provider={}, 원인={}",
                    product.id(), llm.providerName(), e.getMessage());
            return fallback(house, product, candidates);
        }
    }

    private String buildPrompt(House house, Product product, List<Product> candidates) {
        StringBuilder list = new StringBuilder();
        for (Product p : candidates) {
            list.append("- id=%s | %s | %,d원 | %s%n".formatted(p.id(), p.name(), p.price(), p.category()));
        }
        return """
                당신은 패션 브랜드 MCM의 스타일 큐레이터입니다.

                # 방문객의 House
                %s — %s
                %s

                # 방문객이 발견한 상품
                %s (%,d원, %s)

                # 함께 매치할 후보 상품 (같은 House)
                %s

                # 할 일
                1. reason: 이 상품이 왜 이 방문객(%s House)에게 어울리는지 2문장 이내로 씁니다.
                2. story: 이 상품의 무드나 감성을 한두 문장으로 소개합니다. (실제 스펙을 지어내지 말고 무드 위주로)
                3. matches: 위 후보 중 이 상품과 함께 매치하면 좋을 상품 %d개를 골라, 각각 왜 어울리는지 1문장으로 씁니다.

                # 규칙
                - 한국어 존댓말, 따뜻하고 감각적인 톤.
                - matches의 id는 반드시 후보 목록에 있는 id를 그대로 씁니다.

                # 출력 형식 (JSON만, 코드펜스 없이)
                {"reason":"...","story":"...","matches":[{"id":"후보id","reason":"..."},{"id":"후보id","reason":"..."}]}
                """
                .formatted(house.getTitle(), house.getDescription(), String.join(", ", house.getTags()),
                        product.name(), product.price(), product.category(),
                        list.toString().trim(), house.name(), MATCH_COUNT);
    }

    /** LLM 실패 시: 기본 문구 + 같은 House 앞쪽 상품으로 매치. */
    private ProductDetailView fallback(House house, Product product, List<Product> candidates) {
        List<MatchItem> matches = new ArrayList<>();
        for (Product p : candidates) {
            if (matches.size() >= MATCH_COUNT) break;
            matches.add(new MatchItem(p, "같은 " + house.name() + " 무드의 아이템이라 함께 매치하기 좋아요."));
        }
        String reason = "%s House의 취향(%s)과 잘 어울리는 제품이에요."
                .formatted(house.name(), String.join(", ", house.getTags()));
        return new ProductDetailView(house.name(), product, reason, null, true, matches);
    }

    private String stripCodeFence(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            t = t.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").trim();
        }
        return t;
    }
}
