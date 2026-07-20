package com.wedding.platform.operations.feedback.web;

import com.wedding.platform.operations.feedback.application.FeedbackService;
import com.wedding.platform.operations.feedback.persistence.entity.FeedbackPublishStatus;
import com.wedding.platform.operations.feedback.persistence.entity.FeedbackReviewStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customer/feedback")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerFeedbackController {

    private final FeedbackService feedbackService;

    public CustomerFeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping
    public FeedbackDtos.FeedbackPage feedback(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) FeedbackReviewStatus reviewStatus,
            @RequestParam(required = false) FeedbackPublishStatus publishStatus
    ) {
        return feedbackService.customerList(userId(jwt), page, size, reviewStatus, publishStatus);
    }

    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getClaimAsString("uid"));
    }
}
