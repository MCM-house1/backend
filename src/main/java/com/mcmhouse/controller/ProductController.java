package com.mcmhouse.controller;

import com.mcmhouse.domain.House;
import com.mcmhouse.domain.Product;
import com.mcmhouse.catalog.ProductCatalog;
import com.mcmhouse.repository.DiagnosisResultRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * 상품 조회 · House별 상품 추천 API.
 *
 *  GET /api/products               전체 상품
 *  GET /api/products/{house}       특정 House 상품
 *  GET /api/results/{id}/recommendations   진단 결과 기반 추천 상품
 */
@Tag(name = "Products", description = "상품 조회 및 House별 추천")
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ProductController {

    private final ProductCatalog catalog;
    private final DiagnosisResultRepository resultRepository;

    public ProductController(ProductCatalog catalog, DiagnosisResultRepository resultRepository) {
        this.catalog = catalog;
        this.resultRepository = resultRepository;
    }

    @Operation(summary = "전체 상품 조회")
    @GetMapping("/products")
    public List<Product> products() {
        return catalog.all();
    }

    @Operation(summary = "House별 상품 조회",
            description = "house 경로변수는 LEGACY / INSTINCT / FREEDOM / CURIOSITY 중 하나.")
    @GetMapping("/products/{house}")
    public List<Product> productsByHouse(@PathVariable String house) {
        House h = parseHouse(house);
        return catalog.forHouse(h);
    }

    @Operation(summary = "진단 결과 기반 추천 상품",
            description = "진단으로 판별된 최종 House(AI 판별 우선)의 상품을 추천으로 내려준다.")
    @GetMapping("/results/{id}/recommendations")
    public RecommendationView recommendations(@PathVariable Long id) {
        var result = resultRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND,
                        "진단 결과를 찾을 수 없습니다: " + id));
        House house = result.effectiveHouse();
        return new RecommendationView(result.getId(), house.name(), catalog.forHouse(house));
    }

    private House parseHouse(String raw) {
        try {
            return House.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(NOT_FOUND, "알 수 없는 House입니다: " + raw);
        }
    }

    public record RecommendationView(Long resultId, String house, List<Product> products) {}
}
