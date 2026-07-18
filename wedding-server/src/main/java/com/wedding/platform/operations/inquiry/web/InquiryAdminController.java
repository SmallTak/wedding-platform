package com.wedding.platform.operations.inquiry.web;

import com.wedding.platform.operations.inquiry.application.ConsultationLeadService;
import com.wedding.platform.operations.inquiry.persistence.entity.InquiryFollowStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/inquiries")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('/operations/inquiries')")
public class InquiryAdminController {

    private final ConsultationLeadService leadService;

    public InquiryAdminController(ConsultationLeadService leadService) {
        this.leadService = leadService;
    }

    @GetMapping
    public InquiryDtos.InquiryPage leads(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) InquiryFollowStatus followStatus
    ) {
        return leadService.list(userId(jwt), page, size, keyword, followStatus);
    }

    @PutMapping("/{leadId}")
    public InquiryDtos.InquiryResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long leadId,
            @Valid @RequestBody InquiryDtos.UpdateInquiryRequest request,
            HttpServletRequest servletRequest
    ) {
        return leadService.update(userId(jwt), leadId, request, clientIp(servletRequest));
    }

    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getClaimAsString("uid"));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
