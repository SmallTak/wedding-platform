package com.wedding.platform.operations.inquiry.web;

import com.wedding.platform.operations.inquiry.application.ConsultationLeadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/inquiries")
public class PublicInquiryController {

    private final ConsultationLeadService leadService;

    public PublicInquiryController(ConsultationLeadService leadService) {
        this.leadService = leadService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InquiryDtos.InquiryReceipt create(
            @Valid @RequestBody InquiryDtos.CreateInquiryRequest request,
            HttpServletRequest servletRequest
    ) {
        return leadService.create(request, clientIp(servletRequest));
    }

    private String clientIp(HttpServletRequest request) {
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
