package com.mcmhouse.controller;

import com.mcmhouse.domain.Product;
import com.mcmhouse.service.SavedProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 세션(resultId) 기준 상품 저장(하트) API.
 *
 *  GET    /api/results/{id}/saved              저장한 상품 목록
 *  POST   /api/results/{id}/saved              상품 저장
 *  DELETE /api/results/{id}/saved/{productId}  저장 취소
 */
@Tag(name = "Saved", description = "상품 저장(하트)")
@RestController
@CrossOrigin(origins = "*")
public class SavedProductController {

    private final SavedProductService service;

    public SavedProductController(SavedProductService service) {
        this.service = service;
    }

    @Operation(summary = "저장한 상품 목록 (최근순)")
    @GetMapping("/results/{id}/saved")
    public List<Product> list(@PathVariable Long id) {
        return service.list(id);
    }

    @Operation(summary = "상품 저장", description = "이미 저장돼 있으면 그대로 둔다(멱등). 갱신된 저장 목록을 반환.")
    @PostMapping("/results/{id}/saved")
    public List<Product> save(@PathVariable Long id, @RequestBody SaveRequest req) {
        return service.save(id, req.productId());
    }

    @Operation(summary = "저장 취소", description = "저장돼 있지 않아도 에러 없이 통과. 갱신된 저장 목록을 반환.")
    @DeleteMapping("/results/{id}/saved/{productId}")
    public List<Product> unsave(@PathVariable Long id, @PathVariable String productId) {
        return service.unsave(id, productId);
    }

    public record SaveRequest(@NotBlank String productId) {}
}
