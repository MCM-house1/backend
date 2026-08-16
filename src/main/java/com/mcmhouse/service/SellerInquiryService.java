package com.mcmhouse.service;

import com.mcmhouse.catalog.ProductCatalog;
import com.mcmhouse.domain.Product;
import com.mcmhouse.domain.SellerInquiry;
import com.mcmhouse.repository.DiagnosisResultRepository;
import com.mcmhouse.repository.SellerInquiryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * 셀러 상담 요청 내역 저장/조회.
 * "상담 요청이 완료되었습니다" 프로토타입 플로우를 위한 최소 기록 기능.
 */
@Service
public class SellerInquiryService {

    private final SellerInquiryRepository inquiryRepository;
    private final DiagnosisResultRepository resultRepository;
    private final ProductCatalog products;

    public SellerInquiryService(SellerInquiryRepository inquiryRepository,
                                DiagnosisResultRepository resultRepository,
                                ProductCatalog products) {
        this.inquiryRepository = inquiryRepository;
        this.resultRepository = resultRepository;
        this.products = products;
    }

    /** 상담 요청 접수. 어떤 상품에 요청했는지 기록만 남긴다. */
    @Transactional
    public InquiryResult request(Long resultId, String productId) {
        requireResult(resultId);
        Product product = products.findById(productId);
        if (product == null) {
            throw new ResponseStatusException(BAD_REQUEST, "상품을 찾을 수 없습니다: " + productId);
        }
        SellerInquiry saved = inquiryRepository.save(new SellerInquiry(resultId, productId));
        return new InquiryResult(saved.getId(), product, saved.getRequestedAt());
    }

    /** 세션의 상담 요청 내역(최근순). */
    @Transactional(readOnly = true)
    public List<InquiryResult> list(Long resultId) {
        requireResult(resultId);
        return inquiryRepository.findByResultIdOrderByRequestedAtDesc(resultId).stream()
                .map(i -> new InquiryResult(i.getId(), products.findById(i.getProductId()), i.getRequestedAt()))
                .filter(r -> r.product() != null)
                .toList();
    }

    private void requireResult(Long resultId) {
        if (!resultRepository.existsById(resultId)) {
            throw new ResponseStatusException(NOT_FOUND, "진단 결과를 찾을 수 없습니다: " + resultId);
        }
    }

    /** 상담 요청 1건. */
    public record InquiryResult(Long inquiryId, Product product, LocalDateTime requestedAt) {}
}
