package com.mcmhouse.repository;

import com.mcmhouse.domain.SavedProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SavedProductRepository extends JpaRepository<SavedProduct, Long> {

    List<SavedProduct> findByResultIdOrderBySavedAtDesc(Long resultId);

    boolean existsByResultIdAndProductId(Long resultId, String productId);

    void deleteByResultIdAndProductId(Long resultId, String productId);
}
