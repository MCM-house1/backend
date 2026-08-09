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

    public Long getId() { return id; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<House> getAnswers() { return answers; }
    public int getScoreLegacy() { return scoreLegacy; }
    public int getScoreInstinct() { return scoreInstinct; }
    public int getScoreFreedom() { return scoreFreedom; }
    public int getScoreCuriosity() { return scoreCuriosity; }
    public List<House> getFinalHouses() { return finalHouses; }
    public List<ZoneVisit> getVisits() { return visits; }
}
