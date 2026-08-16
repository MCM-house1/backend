package com.mcmhouse.controller;

import com.mcmhouse.service.SellerInquiryService;
import com.mcmhouse.service.SellerInquiryService.InquiryResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 셀러 상담 요청 API. 어떤 상품에 상담을 요청했는지 기록만 남긴다(채팅 X).
 *
 *  POST /api/results/{id}/seller-inquiries   상담 요청 접수
 *  GET  /api/results/{id}/seller-inquiries   상담 요청 내역 조회
 */
@Tag(name = "Seller Inquiry", description = "셀러 상담 요청 내역")
@RestController
@CrossOrigin(origins = "*")
public class SellerInquiryController {

    private final SellerInquiryService service;

    public SellerInquiryController(SellerInquiryService service) {
        this.service = service;
    }

    @Operation(summary = "상담 요청 접수",
            description = "어떤 상품에 상담을 요청했는지 기록하고 완료 응답을 돌려준다. 실제 상담 연결은 없다.")
    @PostMapping("/results/{id}/seller-inquiries")
    public InquiryResponse request(@PathVariable Long id, @RequestBody InquiryRequest req) {
        InquiryResult r = service.request(id, req.productId());
        return new InquiryResponse("completed", "상담 요청이 완료되었습니다.", r);
    }

    @Operation(summary = "상담 요청 내역 조회 (최근순)")
    @GetMapping("/results/{id}/seller-inquiries")
    public List<InquiryResult> list(@PathVariable Long id) {
        return service.list(id);
    }

    public record InquiryRequest(@NotBlank String productId) {}

    public record InquiryResponse(String status, String message, InquiryResult inquiry) {}
}
