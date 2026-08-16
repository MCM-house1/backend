package com.mcmhouse.repository;

import com.mcmhouse.domain.SellerInquiry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SellerInquiryRepository extends JpaRepository<SellerInquiry, Long> {

    List<SellerInquiry> findByResultIdOrderByRequestedAtDesc(Long resultId);
}
