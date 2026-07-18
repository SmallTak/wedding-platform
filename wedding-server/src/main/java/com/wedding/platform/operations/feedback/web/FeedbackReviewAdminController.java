package com.wedding.platform.operations.feedback.web;

import com.wedding.platform.operations.feedback.application.FeedbackService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/feedback")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('/operations/feedback')")
public class FeedbackReviewAdminController {

    private final FeedbackService feedbackService;

    public FeedbackReviewAdminController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping("/{feedbackId}/approve")
    public FeedbackDtos.FeedbackResponse approve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long feedbackId,
            @Valid @RequestBody FeedbackDtos.VersionRequest request,
            HttpServletRequest servletRequest
    ) {
        return feedbackService.approve(userId(jwt), feedbackId, request, clientIp(servletRequest));
    }

    @PostMapping("/{feedbackId}/reject")
    public FeedbackDtos.FeedbackResponse reject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long feedbackId,
            @Valid @RequestBody FeedbackDtos.RejectRequest request,
            HttpServletRequest servletRequest
    ) {
        return feedbackService.reject(userId(jwt), feedbackId, request, clientIp(servletRequest));
    }

    @PostMapping("/{feedbackId}/offline")
    public FeedbackDtos.FeedbackResponse offline(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long feedbackId,
            @Valid @RequestBody FeedbackDtos.OfflineRequest request,
            HttpServletRequest servletRequest
    ) {
        return feedbackService.offline(userId(jwt), feedbackId, request, clientIp(servletRequest));
    }

    @PostMapping("/{feedbackId}/reply/approve")
    public FeedbackDtos.FeedbackResponse approveReply(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long feedbackId,
            @Valid @RequestBody FeedbackDtos.VersionRequest request,
            HttpServletRequest servletRequest
    ) {
        return feedbackService.approveReply(userId(jwt), feedbackId, request, clientIp(servletRequest));
    }

    @PostMapping("/{feedbackId}/reply/reject")
    public FeedbackDtos.FeedbackResponse rejectReply(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long feedbackId,
            @Valid @RequestBody FeedbackDtos.RejectRequest request,
            HttpServletRequest servletRequest
    ) {
        return feedbackService.rejectReply(userId(jwt), feedbackId, request, clientIp(servletRequest));
    }

    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getClaimAsString("uid"));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
