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
     * 개인화 추천 탐험 순서: 점수 내림차순, 동점이면 enum 선언 순서.
     * (Legacy 6 / Curiosity 4 / Freedom 2 / Instinct 0 → LEGACY → CURIOSITY → FREEDOM → INSTINCT)
     */
    public List<House> recommendedRoute() {
        Map<House, Integer> map = scoreMap();
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
    public List<String> getAiQuestions() { return aiQuestions; }
    public List<String> getAiAnswers() { return aiAnswers; }
    public House getAiHouse() { return aiHouse; }
    public String getAiSummary() { return aiSummary; }
    public String getAiReason() { return aiReason; }
    public boolean isAiFallback() { return aiFallback; }
    public boolean isAiAnalyzed() { return aiAnalyzed; }
}
