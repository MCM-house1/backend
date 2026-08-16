package com.mcmhouse.service;

import com.mcmhouse.catalog.ProductCatalog;
import com.mcmhouse.domain.Product;
import com.mcmhouse.domain.SavedProduct;
import com.mcmhouse.repository.DiagnosisResultRepository;
import com.mcmhouse.repository.SavedProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * 세션(resultId) 기준 상품 저장(하트) 기능.
 */
@Service
public class SavedProductService {

    private final SavedProductRepository savedRepository;
    private final DiagnosisResultRepository resultRepository;
    private final ProductCatalog products;

    public SavedProductService(SavedProductRepository savedRepository,
                               DiagnosisResultRepository resultRepository,
                               ProductCatalog products) {
        this.savedRepository = savedRepository;
        this.resultRepository = resultRepository;
        this.products = products;
    }

    /** 상품 저장. 이미 저장돼 있으면 그대로 둔다(멱등). */
    @Transactional
    public List<Product> save(Long resultId, String productId) {
        requireResult(resultId);
        if (products.findById(productId) == null) {
            throw new ResponseStatusException(BAD_REQUEST, "상품을 찾을 수 없습니다: " + productId);
        }
        if (!savedRepository.existsByResultIdAndProductId(resultId, productId)) {
            savedRepository.save(new SavedProduct(resultId, productId));
        }
        return list(resultId);
    }

    /** 저장 취소. 저장돼 있지 않아도 에러 없이 통과(멱등). */
    @Transactional
    public List<Product> unsave(Long resultId, String productId) {
        requireResult(resultId);
        savedRepository.deleteByResultIdAndProductId(resultId, productId);
        return list(resultId);
    }

    /** 저장한 상품 목록(최근 저장순). 카탈로그에서 삭제된 상품은 건너뛴다. */
    @Transactional(readOnly = true)
    public List<Product> list(Long resultId) {
        requireResult(resultId);
        return savedRepository.findByResultIdOrderBySavedAtDesc(resultId).stream()
                .map(SavedProduct::getProductId)
                .map(products::findById)
                .filter(p -> p != null)
                .toList();
    }

    private void requireResult(Long resultId) {
        if (!resultRepository.existsById(resultId)) {
            throw new ResponseStatusException(NOT_FOUND, "진단 결과를 찾을 수 없습니다: " + resultId);
        }
    }
}
