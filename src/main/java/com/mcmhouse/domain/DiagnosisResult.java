package com.mcmhouse.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 진단 결과 1건 = 사용자 1세션.
 * 답변, House별 점수, 최종 House(동점 시 복합), 그리고 이 세션의 Zone 방문 기록을 보관한다.
 */
@Entity
@Table(name = "diagnosis_result")
public class DiagnosisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * A/B 이미지 선택에 주는 가산점. 6문항은 선택지 하나당 2점이므로 한 문항보다 살짝 무겁다.
     *
     * <p>이 값이 3이면 <b>한 문항 차이(2점)까지만 뒤집을 수 있다.</b> 두 문항 이상(4점) 벌어지면
     * 이미지를 반대로 골라도 6문항 결과가 유지된다. 성향이 애매할 때만 마지막 선택이 결정타가 되고,
     * 뚜렷할 때는 문항 결과를 존중한다는 뜻이다.
     *
     * <p>0으로 두면 동점일 때만 선택이 반영되고, 6 이상이면 사실상 선택이 전부를 결정한다.
     */
    public static final int STYLE_CHOICE_BONUS = 3;

    /** 질문 순서대로 선택한 House (answers[i] = i+1번 질문의 선택 House) */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "diagnosis_answer", joinColumns = @JoinColumn(name = "result_id"))
    @Column(name = "house")
    @Enumerated(EnumType.STRING)
    @OrderColumn(name = "question_no")
    private List<House> answers = new ArrayList<>();

    @Column(nullable = false)
    private int scoreLegacy;
    @Column(nullable = false)
    private int scoreInstinct;
    @Column(nullable = false)
    private int scoreFreedom;
    @Column(nullable = false)
    private int scoreCuriosity;

    /** 최고 점수 House 목록. 1개면 단일, 2개 이상이면 복합형(Legacy × Curiosity 등). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "diagnosis_final_house", joinColumns = @JoinColumn(name = "result_id"))
    @Column(name = "house")
    @Enumerated(EnumType.STRING)
    private List<House> finalHouses = new ArrayList<>();

    @OneToMany(mappedBy = "result", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ZoneVisit> visits = new ArrayList<>();

    /**
     * 사용자가 마지막으로 스캔한 Zone. GPS가 아니라 QR/NFC 스캔 결과를 기억하는 값이다.
     * 아직 아무 Zone도 스캔하지 않았으면 null.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "current_zone")
    private House currentZone;

    /* ---------- AI 아이덴티티 분석 ---------- */

    /**
     * A/B 이미지 선택에서 방문객이 고른 House. 그 단계를 거치지 않았으면 null.
     * 최종 점수 계산({@link #finalScoreMap()})에 가산점을 얹을 대상이라 따로 기억한다 —
     * 선택이 뒤집힌 경우 {@code aiHouse}만으로는 무엇을 골랐는지 알 수 없다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "style_choice_house")
    private House styleChoiceHouse;

    /** LLM이 생성한 자연어 후속질문. 아직 생성 전이면 비어 있다. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "diagnosis_ai_question", joinColumns = @JoinColumn(name = "result_id"))
    @Column(name = "text", length = 1000)
    @OrderColumn(name = "seq")
    private List<String> aiQuestions = new ArrayList<>();

    /** 후속질문에 대한 사용자의 자연어 답변. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "diagnosis_ai_answer", joinColumns = @JoinColumn(name = "result_id"))
    @Column(name = "text", length = 2000)
    @OrderColumn(name = "seq")
    private List<String> aiAnswers = new ArrayList<>();

    /** LLM이 최종 판별한 House. 분석 전이거나 폴백된 경우 null. */
    @Enumerated(EnumType.STRING)
    @Column(name = "ai_house")
    private House aiHouse;

    /** 사용자에게 보여줄 한두 문장 요약. */
    @Column(name = "ai_summary", length = 1000)
    private String aiSummary;

    /** 그렇게 판별한 근거. */
    @Column(name = "ai_reason", length = 2000)
    private String aiReason;

    /** LLM 호출이 실패해 규칙기반 점수 결과를 사용했는지 여부. */
    @Column(name = "ai_fallback", nullable = false)
    private boolean aiFallback;

    /** 분석이 한 번이라도 수행되었는지. */
    @Column(name = "ai_analyzed", nullable = false)
    private boolean aiAnalyzed;

    protected DiagnosisResult() {}

    public DiagnosisResult(List<House> answers) {
        this.answers = new ArrayList<>(answers);
        applyScores(answers);
    }

    /** 답변 리스트로부터 점수 합산 및 최종 House 판별. */
    private void applyScores(List<House> answers) {
        Map<House, Integer> map = new EnumMap<>(House.class);
        for (House h : House.values()) map.put(h, 0);
        for (House h : answers) {
            if (h != null) map.merge(h, 2, Integer::sum);
        }
        this.scoreLegacy = map.get(House.LEGACY);
        this.scoreInstinct = map.get(House.INSTINCT);
        this.scoreFreedom = map.get(House.FREEDOM);
        this.scoreCuriosity = map.get(House.CURIOSITY);

        int max = map.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        this.finalHouses = new ArrayList<>();
        for (House h : House.values()) {            // enum 선언 순서 유지
            if (map.get(h) == max) finalHouses.add(h);
        }
    }

    public Map<House, Integer> scoreMap() {
        Map<House, Integer> map = new EnumMap<>(House.class);
        map.put(House.LEGACY, scoreLegacy);
        map.put(House.INSTINCT, scoreInstinct);
        map.put(House.FREEDOM, scoreFreedom);
        map.put(House.CURIOSITY, scoreCuriosity);
        return map;
    }

    /**
     * 6문항 점수에 A/B 이미지 선택 가산점까지 반영한 최종 점수.
     *
     * <p>최종 House는 이 점수로 결정되므로, 화면의 점수 막대도 이 값으로 그려야
     * "1등 막대와 결과가 다르다"처럼 보이지 않는다. A/B 단계를 거치지 않았으면 6문항 점수와 같다.
     */
    public Map<House, Integer> finalScoreMap() {
        Map<House, Integer> map = scoreMap();
        if (styleChoiceHouse != null) {
            map.merge(styleChoiceHouse, STYLE_CHOICE_BONUS, Integer::sum);
        }
        return map;
    }

    /** A/B 이미지 선택에서 방문객이 고른 House를 기록한다. */
    public void applyStyleChoice(House chosen) {
        this.styleChoiceHouse = chosen;
    }

    /**
     * 개인화 추천 탐험 순서: 점수 내림차순, 동점이면 enum 선언 순서.
     * (Legacy 6 / Curiosity 4 / Freedom 2 / Instinct 0 → LEGACY → CURIOSITY → FREEDOM → INSTINCT)
     */
    public List<House> recommendedRoute() {
        return routeOf(finalScoreMap());
    }

    /**
     * 6문항 점수만으로 정렬한 순서. A/B 후보(1·2등)를 뽑을 때 쓴다.
     * 선택 가산점이 섞이면 이미 고른 House가 계속 1등으로 올라와 후보가 흔들리므로 분리한다.
     */
    public List<House> scoreRoute() {
        return routeOf(scoreMap());
    }

    private List<House> routeOf(Map<House, Integer> map) {
        List<House> route = new ArrayList<>(List.of(House.values()));
        route.sort((a, b) -> {
            int cmp = Integer.compare(map.get(b), map.get(a));
            if (cmp != 0) return cmp;
            return Integer.compare(a.ordinal(), b.ordinal());
        });
        return route;
    }

    public boolean hasVisited(House house) {
        return visits.stream().anyMatch(v -> v.getHouse() == house);
    }

    /** 해당 House를 방문(QR 스캔)한 시각. 미방문이면 null. */
    public LocalDateTime visitedAt(House house) {
        return visits.stream()
                .filter(v -> v.getHouse() == house)
                .map(ZoneVisit::getVisitedAt)
                .findFirst()
                .orElse(null);
    }

    public void addVisit(ZoneVisit visit) {
        visit.setResult(this);
        visits.add(visit);
    }

    /** 스캔한 Zone을 현재 위치로 기록한다. */
    public void markCurrentZone(House house) {
        this.currentZone = house;
    }

    /** LLM이 생성한 후속질문을 저장한다. 재생성 시 기존 질문/답변은 초기화한다. */
    public void setAiQuestions(List<String> questions) {
        this.aiQuestions = new ArrayList<>(questions);
        this.aiAnswers = new ArrayList<>();
        this.aiAnalyzed = false;
    }

    /**
     * 최종 판별 결과를 기록한다.
     *
     * @param house    LLM이 고른 House. 폴백된 경우 null을 넘긴다.
     * @param fallback LLM 실패로 규칙기반 결과를 쓴 경우 true
     */
    public void applyAiAnalysis(List<String> answers, House house,
                                String summary, String reason, boolean fallback) {
        this.aiAnswers = new ArrayList<>(answers);
        this.aiHouse = house;
        this.aiSummary = summary;
        this.aiReason = reason;
        this.aiFallback = fallback;
        this.aiAnalyzed = true;
    }

    /**
     * 사용자에게 보여줄 최종 House.
     * LLM 판별이 있으면 그것을, 없으면 규칙기반 최고점 House를 쓴다.
     */
    public House effectiveHouse() {
        if (aiHouse != null) return aiHouse;
        return finalHouses.isEmpty() ? House.LEGACY : finalHouses.get(0);
    }

    public Long getId() { return id; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<House> getAnswers() { return answers; }
    public int getScoreLegacy() { return scoreLegacy; }
    public int getScoreInstinct() { return scoreInstinct; }
    public int getScoreFreedom() { return scoreFreedom; }
    public int getScoreCuriosity() { return scoreCuriosity; }
    public List<House> getFinalHouses() { return finalHouses; }
    public List<ZoneVisit> getVisits() { return visits; }
    public House getCurrentZone() { return currentZone; }
    public House getStyleChoiceHouse() { return styleChoiceHouse; }
    public List<String> getAiQuestions() { return aiQuestions; }
    public List<String> getAiAnswers() { return aiAnswers; }
    public House getAiHouse() { return aiHouse; }
    public String getAiSummary() { return aiSummary; }
    public String getAiReason() { return aiReason; }
    public boolean isAiFallback() { return aiFallback; }
    public boolean isAiAnalyzed() { return aiAnalyzed; }
}
