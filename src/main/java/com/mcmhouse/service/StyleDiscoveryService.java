package com.mcmhouse.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcmhouse.catalog.ProductCatalog;
import com.mcmhouse.domain.House;
import com.mcmhouse.domain.Product;
import com.mcmhouse.domain.StyleDiscovery;
import com.mcmhouse.dto.RecommendationDtos.MatchItem;
import com.mcmhouse.dto.StyleDiscoveryDtos.DiscoveryArchiveItem;
import com.mcmhouse.dto.StyleDiscoveryDtos.StyleDiscoveryRequest;
import com.mcmhouse.dto.StyleDiscoveryDtos.StyleDiscoveryView;
import com.mcmhouse.llm.LlmClient;
import com.mcmhouse.repository.DiagnosisResultRepository;
import com.mcmhouse.repository.StyleDiscoveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * 셀카 무드 분석(Style Discovery).
 *
 * <p>미션에서 촬영한 거울 셀카를 Gemini 비전이 분석해 스타일 제목/키워드/인상과
 * 함께 매치할 상품(COMPLETE THE LOOK)을 생성한다. 셀카는 base64로 저장해 패스포트 아카이브에서 재조회한다.
 *
 * <p>이미지가 없거나 비전 호출이 실패하면 제품+House 기반 폴백으로 안전하게 채운다.
 */
@Service
public class StyleDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(StyleDiscoveryService.class);
    private static final int MATCH_COUNT = 2;
    private static final int KEYWORD_COUNT = 3;

    private final LlmClient llm;
    private final ProductCatalog products;
    private final DiagnosisResultRepository resultRepository;
    private final StyleDiscoveryRepository discoveryRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    public StyleDiscoveryService(LlmClient llm, ProductCatalog products,
                                 DiagnosisResultRepository resultRepository,
                                 StyleDiscoveryRepository discoveryRepository) {
        this.llm = llm;
        this.products = products;
        this.resultRepository = resultRepository;
        this.discoveryRepository = discoveryRepository;
    }

    /**
     * 셀카 무드 분석 + 저장. House당 최신 1건을 유지한다.
     *
     * <p>기준 상품은 요청으로 받지 않는다. 미션에 상품을 고르는 화면이 없으므로 셀카에서 AI가 찾아낸다.
     * 이때 후보는 해당 House의 상품으로 한정한다 — 전체 27개를 열어두면 오답이 늘고,
     * 방문객은 그 House의 Zone에서 촬영했기 때문이다.
     */
    @Transactional
    public StyleDiscoveryView analyze(Long resultId, StyleDiscoveryRequest req) {
        if (!resultRepository.existsById(resultId)) {
            throw new ResponseStatusException(NOT_FOUND, "진단 결과를 찾을 수 없습니다: " + resultId);
        }
        House house = parseHouse(req.house());

        Analysis analysis = runAnalysis(house, req.photo());
        Product pick = analysis.pick;
        String pickId = pick == null ? null : pick.id();

        // House당 1건 upsert
        StyleDiscovery entity = discoveryRepository.findByResultIdAndHouse(resultId, house)
                .orElseGet(() -> new StyleDiscovery(resultId, house, nullToEmpty(req.photo()), pickId));
        entity.updatePhoto(nullToEmpty(req.photo()), pickId);
        entity.applyAnalysis(analysis.title, analysis.description, analysis.keywords,
                analysis.impression, analysis.fallback);
        StyleDiscovery saved = discoveryRepository.save(entity);

        return new StyleDiscoveryView(
                saved.getId(), house.name(),
                analysis.title, analysis.description, analysis.keywords, analysis.impression,
                pick, analysis.matches, analysis.fallback
        );
    }

    /** 패스포트 아카이브: 저장된 셀카+스타일 목록(최근순). */
    @Transactional(readOnly = true)
    public List<DiscoveryArchiveItem> archive(Long resultId) {
        if (!resultRepository.existsById(resultId)) {
            throw new ResponseStatusException(NOT_FOUND, "진단 결과를 찾을 수 없습니다: " + resultId);
        }
        return discoveryRepository.findByResultIdOrderByCreatedAtDesc(resultId).stream()
                .map(d -> new DiscoveryArchiveItem(
                        d.getId(), d.getHouse().name(), d.getPhotoDataUrl(),
                        d.getStyleTitle(), d.getKeywords(), d.getCreatedAt()))
                .toList();
    }

    /* ---------- 내부: 분석 ---------- */

    private Analysis runAnalysis(House house, String photo) {
        String base64 = extractBase64(photo);
        if (base64 == null) {                        // 이미지 없음 → 상품을 찾을 근거가 없다
            return fallback(house, null);
        }
        try {
            String prompt = buildPrompt(house);
            JsonNode root = mapper.readTree(stripCodeFence(
                    llm.completeWithImage(prompt, base64, extractMime(photo))));

            String title = root.path("styleTitle").asText("").trim();
            String description = root.path("styleDescription").asText("").trim();
            String impression = root.path("impression").asText("").trim();

            List<String> keywords = new ArrayList<>();
            for (JsonNode k : root.path("keywords")) {
                if (keywords.size() < KEYWORD_COUNT && !k.asText("").isBlank()) keywords.add(k.asText().trim());
            }

            // AI가 고른 상품. 엉뚱한 House 것을 골라오면 인정하지 않는다.
            Product pick = products.findById(root.path("pickedProductId").asText("").trim());
            if (pick != null && pick.house() != house) {
                pick = null;
            }

            List<MatchItem> matches = new ArrayList<>();
            for (JsonNode m : root.path("matches")) {
                Product matched = products.findById(m.path("id").asText(""));
                boolean isPick = matched != null && pick != null && matched.id().equals(pick.id());
                if (matched != null && !isPick && matches.size() < MATCH_COUNT) {
                    matches.add(new MatchItem(matched, m.path("reason").asText("").trim()));
                }
            }
            if (title.isBlank() || keywords.isEmpty() || matches.isEmpty()) {
                return fallback(house, pick);
            }
            return new Analysis(title, description, keywords, impression, pick, matches, false);

        } catch (Exception e) {
            log.warn("셀카 무드 분석 실패 → 폴백. house={}, provider={}, 원인={}",
                    house, llm.providerName(), e.getMessage());
            return fallback(house, null);
        }
    }

    /**
     * 무게중심은 "기준 상품과 어울리는가"다. 셀카는 무드를 참고하는 보조 정보로 쓴다.
     *
     * <p>기준 상품을 지정받지 못했으면, 같은 셀카로 상품 특정까지 함께 시킨다.
     * 호출을 두 번으로 나누면 응답이 느려지고 두 판단이 어긋날 수 있어 한 번에 처리한다.
     * 상품 후보는 해당 House로 한정해 객관식으로 만든다 — 서술형으로 물으면 없는 상품을 지어낸다.
     */
    private String buildPrompt(House house) {
        return """
                당신은 패션 브랜드 MCM의 스타일 큐레이터입니다.

                # 1단계 — 셀카 속 상품 찾기
                방문객은 아래 상품 중 하나를 들거나 착용한 채 촬영했습니다.
                셀카를 보고 어떤 상품인지 특정해 pickedProductId에 그 id를 쓰세요.

                %s

                확신이 서지 않으면 형태·색·카테고리가 가장 가까운 것을 고르세요.
                목록에 없는 id를 지어내면 안 됩니다.

                # 참고 정보
                방문객의 House: %s — %s (%s)

                # 매치 후보 상품 (전체 House)
                %s

                # 2단계 — 할 일
                **1단계에서 찾은 상품과 진짜 잘 어울리는 조합**을 만드는 것이 목적입니다.
                1. pickedProductId: 셀카 속 인물이 들거나 착용한 상품의 id (1단계 목록 중에서).
                2. styleTitle: 이 사람의 스타일을 한 문장으로. 예) "깔끔하지만 평범하지 않게"
                3. styleDescription: 어떤 스타일을 선호하는지 2문장 이내로 서술.
                4. keywords: 스타일을 요약하는 키워드 %d개. 예) 정돈된, 도시적인, 존재감 있는
                5. impression: 이 스타일이 주는 인상 2문장 이내.
                6. matches: 매치 후보 중 **찾은 상품과 스타일이 진짜 잘 어울리는** 상품 %d개를 골라 각각 왜 어울리는지 1문장.

                # 규칙
                - matches를 고를 때 기준은 1단계에서 찾은 상품과의 스타일 궁합입니다. House가 같은지는 부차적입니다.
                - matches에 pickedProductId와 같은 상품을 넣지 마세요.
                - 셀카 속 인물의 외모/신원을 묘사하거나 추측하지 말 것. 스타일·무드만.
                - 한국어 존댓말, 따뜻하고 감각적인 톤.
                - 모든 id는 반드시 위 목록의 id를 그대로 쓸 것.

                # 출력 형식 (JSON만, 코드펜스 없이)
                {"pickedProductId":"1단계 목록의 id","styleTitle":"...","styleDescription":"...","keywords":["..","..",".."],"impression":"...","matches":[{"id":"후보id","reason":"..."},{"id":"후보id","reason":"..."}]}
                """
                .formatted(describeProducts(products.forHouse(house)),
                        house.getTitle(), house.getDescription(), String.join(", ", house.getTags()),
                        describeProducts(products.all()),
                        KEYWORD_COUNT, MATCH_COUNT);
    }

    /** 프롬프트에 넣을 상품 목록 한 덩어리. */
    private String describeProducts(List<Product> list) {
        StringBuilder sb = new StringBuilder();
        for (Product p : list) {
            sb.append("- id=%s | %s | %,d원 | %s | %s House%n"
                    .formatted(p.id(), p.name(), p.price(), p.category(), p.house()));
        }
        return sb.toString().trim();
    }

    /**
     * 이미지 없음/실패 시 폴백. 매치는 선택 상품과 같은 House를 우선하되 전체 후보에서 채운다.
     */
    private Analysis fallback(House house, Product pick) {
        House base = pick == null ? house : pick.house();
        List<Product> pool = products.all().stream()
                .filter(p -> pick == null || !p.id().equals(pick.id()))
                .toList();

        List<Product> sameHouseFirst = new ArrayList<>();
        pool.stream().filter(p -> p.house() == base).forEach(sameHouseFirst::add);
        pool.stream().filter(p -> p.house() != base).forEach(sameHouseFirst::add);

        List<MatchItem> matches = new ArrayList<>();
        for (Product p : sameHouseFirst) {
            if (matches.size() >= MATCH_COUNT) break;
            matches.add(new MatchItem(p, p.house() == base
                    ? "같은 " + base.name() + " 무드의 아이템이라 함께 매치하기 좋아요."
                    : "스타일 결이 비슷해 함께 매치하기 좋아요."));
        }
        return new Analysis(
                house.getTitle() + " 무드",
                house.getDescription(),
                new ArrayList<>(house.getTags()),
                "%s의 취향이 분명하게 드러나는 스타일이에요.".formatted(house.name()),
                pick, matches, true
        );
    }

    /* ---------- 유틸 ---------- */

    private House parseHouse(String raw) {
        if (raw == null) throw new ResponseStatusException(BAD_REQUEST, "house는 필수입니다.");
        try {
            return House.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(BAD_REQUEST, "알 수 없는 House입니다: " + raw);
        }
    }

    /** data URL에서 순수 base64만 추출. 이미지가 없으면 null. */
    private String extractBase64(String photo) {
        if (photo == null || photo.isBlank()) return null;
        int comma = photo.indexOf(',');
        String base64 = comma >= 0 ? photo.substring(comma + 1) : photo;
        return base64.isBlank() ? null : base64;
    }

    /** data URL에서 mime 타입 추출. 없으면 image/jpeg. */
    private String extractMime(String photo) {
        if (photo != null && photo.startsWith("data:")) {
            int semi = photo.indexOf(';');
            if (semi > 5) return photo.substring(5, semi);
        }
        return "image/jpeg";
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private String stripCodeFence(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            t = t.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").trim();
        }
        return t;
    }

    /**
     * @param pick 기준 상품. 프론트가 지정했거나 AI가 셀카에서 찾은 것. 둘 다 없으면 null
     */
    private record Analysis(String title, String description, List<String> keywords,
                            String impression, Product pick, List<MatchItem> matches, boolean fallback) {}
}
