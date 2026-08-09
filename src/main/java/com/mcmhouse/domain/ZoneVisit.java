package com.mcmhouse.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Zone 방문 인증 기록 = 해당 House Stamp 1개.
 * 한 결과(세션)당 House별 최대 1건.
 */
@Entity
@Table(
        name = "zone_visit",
        uniqueConstraints = @UniqueConstraint(columnNames = {"result_id", "house"})
)
public class ZoneVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "result_id", nullable = false)
    private DiagnosisResult result;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private House house;

    @Column(nullable = false, updatable = false)
    private LocalDateTime visitedAt = LocalDateTime.now();

    protected ZoneVisit() {}

    public ZoneVisit(House house) {
        this.house = house;
    }

    public Long getId() { return id; }
    public DiagnosisResult getResult() { return result; }
    public House getHouse() { return house; }
    public LocalDateTime getVisitedAt() { return visitedAt; }

    void setResult(DiagnosisResult result) { this.result = result; }
}
