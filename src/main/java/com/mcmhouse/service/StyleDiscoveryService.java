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
import java.util.Map;

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

    /**
     * House별 대표 상품(YOUR PICK). 데모용 고정 매핑이다.
     *
     * <p>원래는 셀카에서 AI가 상품을 특정하게 했으나, 실측 결과 신뢰할 수 없어 되돌렸다.
     * 카탈로그 27개에 실제 매장 상품(예: 스타크 백팩)이 없어 정답 자체가 없는 경우가 있고,
     * 있더라도 같은 사진에서 매번 같은 상품이 나오지 않았다.
     * 화면의 YOUR PICK 카드는 비어 있으면 안 되므로, 확실하게 채워지는 쪽을 택한다.
     *
     * <p>개선하려면 매장 상품을 모두 카탈로그에 넣고 상품별 참조 이미지로 유사도를 비교해야 한다.
     */
    private static final Map<House, String> DEMO_PICK = Map.of(
            House.LEGACY,    "01_REC3",   // Aren 비세토스 레더 믹스 베니티 케이스
            House.INSTINCT,  "02_REC2",   // Aren 비세토스 트래버텅글 크로스바디
            House.FREEDOM,   "03_REC1",   // Aren 비세토스 슬링백
            House.CURIOSITY, "04_REC1"    // Aren 듀오 오브 럭시 비세토스 숄더 카프스킨
    );

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
     * <p>기준 상품은 {@link #DEMO_PICK}으로 House마다 하나씩 고정한다. 요청으로도 받지 않고
     * 셀카에서 찾아내지도 않는다. 이유는 상수 주석 참고.
     */
    @Transactional
    public StyleDiscoveryView analyze(Long resultId, StyleDiscoveryRequest req) {
        if (!resultRepository.existsById(resultId)) {
            throw new ResponseStatusException(NOT_FOUND, "진단 결과를 찾을 수 없습니다: " + resultId);
        }
        House house = parseHouse(req.house());
        Product pick = products.findById(DEMO_PICK.get(house));

        Analysis analysis = runAnalysis(house, pick, req.photo());
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
                .map(this::toArchiveItem)
                .toList();
    }

    /**
     * 패스포트에서 House 카드를 눌렀을 때 이어지는 화면용: 해당 House의 디스커버리 1건.
     * 아직 그 House에서 셀카 무드 분석을 하지 않았으면 404.
     */
    @Transactional(readOnly = true)
    public DiscoveryArchiveItem findByHouse(Long resultId, String rawHouse) {
        if (!resultRepository.existsById(resultId)) {
            throw new ResponseStatusException(NOT_FOUND, "진단 결과를 찾을 수 없습니다: " + resultId);
        }
        House house = parseHouse(rawHouse);
        return discoveryRepository.findByResultIdAndHouse(resultId, house)
                .map(this::toArchiveItem)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND,
                        "%s House의 디스커버리 기록이 아직 없습니다.".formatted(house.name())));
    }

    private DiscoveryArchiveItem toArchiveItem(StyleDiscovery d) {
        return new DiscoveryArchiveItem(
                d.getId(), d.getHouse().name(), d.getPhotoDataUrl(),
                d.getStyleTitle(), d.getKeywords(), d.getCreatedAt());
    }

    /* ---------- 내부: 분석 ---------- */

    private Analysis runAnalysis(House house, Product pick, String photo) {
        String base64 = extractBase64(photo);
        if (base64 == null) {                        // 이미지 없음 → 무드를 읽을 근거가 없다
            return fallback(house, pick);
        }
        try {
            String prompt = buildPrompt(house, pick);
            JsonNode root = mapper.readTree(stripCodeFence(
                    llm.completeWithImage(prompt, base64, extractMime(photo))));

            String title = root.path("styleTitle").asText("").trim();
            String description = root.path("styleDescription").asText("").trim();
            String impression = root.path("impression").asText("").trim();

            List<String> keywords = new ArrayList<>();
            for (JsonNode k : root.path("keywords")) {
                if (keywords.size() < KEYWORD_COUNT && !k.asText("").isBlank()) keywords.add(k.asText().trim());
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
            return fallback(house, pick);
        }
    }

    /**
     * 무게중심은 "기준 상품과 어울리는가"다. 셀카는 무드(색감·실루엣·분위기)를 읽는 데 쓴다.
     * 기준 상품은 고정이므로 프롬프트에서 상품을 특정하게 시키지 않는다.
     */
    private String buildPrompt(House house, Product pick) {
        String pickLine = pick == null ? "(없음)"
                : "%s (%,d원, %s, %s House)".formatted(pick.name(), pick.price(), pick.category(), pick.house());
        List<Product> matchPool = products.all().stream()
                .filter(p -> pick == null || !p.id().equals(pick.id()))
                .toList();

        return """
                당신은 패션 브랜드 MCM의 스타일 큐레이터입니다.

                # 기준 상품 (방문객이 이번 미션에서 발견한 상품)
                %s

                # 참고 정보
                방문객의 House: %s — %s (%s)
                방문객이 매장에서 찍은 거울 셀카가 함께 제공됩니다. 셀카에서 무드(색감, 실루엣, 분위기)를 읽으세요.

                # 매치 후보 상품 (전체 House)
                %s

                # 할 일
                **기준 상품과 진짜 잘 어울리는 조합**을 만드는 것이 목적입니다.
                1. styleTitle: 이 사람의 스타일을 한 문장으로. 예) "깔끔하지만 평범하지 않게"
                2. styleDescription: 어떤 스타일을 선호하는지 2문장 이내로 서술.
                3. keywords: 스타일을 요약하는 키워드 %d개. 예) 정돈된, 도시적인, 존재감 있는
                4. impression: 이 스타일이 주는 인상 2문장 이내.
                5. matches: 매치 후보 중 **기준 상품과 스타일이 진짜 잘 어울리는** 상품 %d개를 골라 각각 왜 어울리는지 1문장.

                # 규칙
                - matches를 고를 때 기준은 기준 상품과의 스타일 궁합입니다. House가 같은지는 부차적입니다.
                - matches는 서로 다른 카테고리(BAG/SHOES/WALLET/APPAREL/ACCESSORY/TRAVEL)로 고르고,
                  기준 상품과도 카테고리가 겹치지 않게 하세요. 예: 기준 상품이 가방이면 신발·지갑·
                  액세서리 등 다른 종류에서 고를 것 (가방+가방처럼 같은 종류로만 채우지 말 것).
                - 셀카 속 인물의 외모/신원을 묘사하거나 추측하지 말 것. 스타일·무드만.
                - 한국어 존댓말, 따뜻하고 감각적인 톤.
                - matches의 id는 반드시 위 후보 목록의 id를 그대로 쓸 것.

                # 출력 형식 (JSON만, 코드펜스 없이)
                {"styleTitle":"...","styleDescription":"...","keywords":["..","..",".."],"impression":"...","matches":[{"id":"후보id","reason":"..."},{"id":"후보id","reason":"..."}]}
                """
                .formatted(pickLine,
                        house.getTitle(), house.getDescription(), String.join(", ", house.getTags()),
                        describeProducts(matchPool),
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
     *
     * <p>같은 카테고리(가방+가방 등)만 뽑히지 않도록, 이미 고른 카테고리(기준 상품 포함)는
     * 최대한 피해서 다양한 종류로 채운다. 후보가 부족할 때만 카테고리 중복을 허용한다.
     */
    private Analysis fallback(House house, Product pick) {
        House base = pick == null ? house : pick.house();
        List<Product> sameHouse = products.all().stream()
                .filter(p -> pick == null || !p.id().equals(pick.id()))
                .filter(p -> p.house() == base)
                .toList();
        List<Product> otherHouse = products.all().stream()
                .filter(p -> p.house() != base)
                .toList();

        java.util.Set<String> usedCategories = new java.util.HashSet<>();
        if (pick != null) usedCategories.add(pick.category());

        List<MatchItem> matches = new ArrayList<>();
        // 1차: 같은 House, 카테고리는 겹치지 않게
        for (Product p : sameHouse) {
            if (matches.size() >= MATCH_COUNT) break;
            if (!usedCategories.add(p.category())) continue;
            matches.add(new MatchItem(p, "같은 " + base.name() + " 무드의 아이템이라 함께 매치하기 좋아요."));
        }
        // 2차: 후보가 모자라면 같은 House에서 카테고리 중복 허용
        for (Product p : sameHouse) {
            if (matches.size() >= MATCH_COUNT) break;
            if (matches.stream().anyMatch(m -> m.product().id().equals(p.id()))) continue;
            matches.add(new MatchItem(p, "같은 " + base.name() + " 무드의 아이템이라 함께 매치하기 좋아요."));
        }
        // 3차: 그래도 모자라면 다른 House에서 채움
        for (Product p : otherHouse) {
            if (matches.size() >= MATCH_COUNT) break;
            matches.add(new MatchItem(p, "스타일 결이 비슷해 함께 매치하기 좋아요."));
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
