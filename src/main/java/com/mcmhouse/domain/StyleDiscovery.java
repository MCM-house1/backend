package com.mcmhouse.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 미션에서 촬영한 거울 셀카에 대한 스타일 분석 결과 = 패스포트 아카이브 1건.
 * 셀카 이미지는 base64로 저장하고, 세션(resultId) × House당 최신 1건만 유지한다.
 */
@Entity
@Table(
        name = "style_discovery",
        uniqueConstraints = @UniqueConstraint(columnNames = {"result_id", "house"})
)
public class StyleDiscovery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "result_id", nullable = false)
    private Long resultId;

    /** 이 셀카가 속한 House 미션. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private House house;

    /**
     * 촬영한 셀카. data URL 형태(data:image/jpeg;base64,...)로 저장해 그대로 <img>에 쓸 수 있게 한다.
     * 실제 셀카는 base64로 수백 KB~수 MB라 TEXT(64KB 한도)로는 부족해 LONGTEXT로 명시한다.
     */
    @Lob
    @Column(name = "photo_data_url", nullable = false, columnDefinition = "LONGTEXT")
    private String photoDataUrl;

    /** AI가 셀카에서 특정한 상품 id. 특정 실패 시 null. */
    @Column(name = "detected_product_id")
    private String detectedProductId;

    /** 분석된 스타일 제목. 예: "깔끔하지만 평범하지 않게" */
    @Column(name = "style_title", length = 200)
    private String styleTitle;

    /** 스타일 설명. */
    @Column(name = "style_description", length = 1000)
    private String styleDescription;

    /** 스타일 키워드. 예: 정돈된 / 도시적인 / 존재감 있는 */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "style_discovery_keyword", joinColumns = @JoinColumn(name = "discovery_id"))
    @Column(name = "keyword")
    @OrderColumn(name = "seq")
    private List<String> keywords = new ArrayList<>();

    /** 이 스타일이 주는 인상. */
    @Column(name = "impression", length = 1000)
    private String impression;

    /** 비전 분석 실패로 폴백(제품+House 기반)했는지. */
    @Column(name = "fallback", nullable = false)
    private boolean fallback;

    /** COMPLETE THE LOOK 매치 상품(id)과 매치 이유. 재조회 시 그대로 다시 내려주기 위해 저장한다. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "style_discovery_match", joinColumns = @JoinColumn(name = "discovery_id"))
    @OrderColumn(name = "seq")
    private List<MatchRecord> matches = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected StyleDiscovery() {}

    public StyleDiscovery(Long resultId, House house, String photoDataUrl, String detectedProductId) {
        this.resultId = resultId;
        this.house = house;
        this.photoDataUrl = photoDataUrl;
        this.detectedProductId = detectedProductId;
    }

    /** 분석 결과를 채운다. 재분석 시 덮어쓴다. */
    public void applyAnalysis(String styleTitle, String styleDescription, List<String> keywords,
                              String impression, boolean fallback, List<MatchRecord> matches) {
        this.styleTitle = styleTitle;
        this.styleDescription = styleDescription;
        this.keywords = new ArrayList<>(keywords);
        this.impression = impression;
        this.fallback = fallback;
        this.matches = new ArrayList<>(matches);
    }

    /** COMPLETE THE LOOK 매치 상품 id + 매치 이유 1건. */
    @Embeddable
    public static class MatchRecord {
        @Column(name = "product_id")
        private String productId;
        @Column(name = "reason", length = 500)
        private String reason;

        protected MatchRecord() {}

        public MatchRecord(String productId, String reason) {
            this.productId = productId;
            this.reason = reason;
        }

        public String getProductId() { return productId; }
        public String getReason() { return reason; }
    }

    public void updatePhoto(String photoDataUrl, String detectedProductId) {
        this.photoDataUrl = photoDataUrl;
        this.detectedProductId = detectedProductId;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getResultId() { return resultId; }
    public House getHouse() { return house; }
    public String getPhotoDataUrl() { return photoDataUrl; }
    public String getDetectedProductId() { return detectedProductId; }
    public String getStyleTitle() { return styleTitle; }
    public String getStyleDescription() { return styleDescription; }
    public List<String> getKeywords() { return keywords; }
    public String getImpression() { return impression; }
    public boolean isFallback() { return fallback; }
    public List<MatchRecord> getMatches() { return matches; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
