package com.wedding.platform.operations.feedback.web;

import com.wedding.platform.operations.feedback.application.FeedbackService;
import com.wedding.platform.operations.feedback.persistence.entity.FeedbackPublishStatus;
import com.wedding.platform.operations.feedback.persistence.entity.FeedbackReviewStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feedback")
@PreAuthorize("hasAuthority('/operations/feedback')")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping
    public FeedbackDtos.FeedbackPage feedback(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) FeedbackReviewStatus reviewStatus,
            @RequestParam(required = false) FeedbackPublishStatus publishStatus,
            @RequestParam(required = false) Long projectId
    ) {
        return feedbackService.list(userId(jwt), page, size, reviewStatus, publishStatus, projectId);
    }

    @GetMapping("/options")
    public FeedbackDtos.FeedbackOptions options(@AuthenticationPrincipal Jwt jwt) {
        return feedbackService.options(userId(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FeedbackDtos.FeedbackResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody FeedbackDtos.UpsertFeedbackRequest request,
            HttpServletRequest servletRequest
    ) {
        return feedbackService.create(userId(jwt), request, clientIp(servletRequest));
    }

    @PutMapping("/{feedbackId}")
    public FeedbackDtos.FeedbackResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long feedbackId,
            @Valid @RequestBody FeedbackDtos.UpsertFeedbackRequest request,
            HttpServletRequest servletRequest
    ) {
        return feedbackService.update(userId(jwt), feedbackId, request, clientIp(servletRequest));
    }

    @PutMapping("/{feedbackId}/reply")
    public FeedbackDtos.FeedbackResponse reply(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long feedbackId,
            @Valid @RequestBody FeedbackDtos.UpsertReplyRequest request,
            HttpServletRequest servletRequest
    ) {
        return feedbackService.upsertReply(userId(jwt), feedbackId, request, clientIp(servletRequest));
    }

    @DeleteMapping("/{feedbackId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long feedbackId,
            @RequestParam Long version,
            HttpServletRequest servletRequest
    ) {
        feedbackService.withdraw(userId(jwt), feedbackId, version, clientIp(servletRequest));
    }

    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getClaimAsString("uid"));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
