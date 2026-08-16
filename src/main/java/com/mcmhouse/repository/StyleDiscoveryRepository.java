package com.mcmhouse.repository;

import com.mcmhouse.domain.House;
import com.mcmhouse.domain.StyleDiscovery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StyleDiscoveryRepository extends JpaRepository<StyleDiscovery, Long> {

    List<StyleDiscovery> findByResultIdOrderByCreatedAtDesc(Long resultId);

    Optional<StyleDiscovery> findByResultIdAndHouse(Long resultId, House house);
}
