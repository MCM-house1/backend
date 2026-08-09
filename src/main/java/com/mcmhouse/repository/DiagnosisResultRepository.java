package com.mcmhouse.repository;

import com.mcmhouse.domain.DiagnosisResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiagnosisResultRepository extends JpaRepository<DiagnosisResult, Long> {
}
