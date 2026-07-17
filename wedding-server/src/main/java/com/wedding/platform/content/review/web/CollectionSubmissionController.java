package com.wedding.platform.content.review.web;

import com.wedding.platform.content.review.application.CollectionReviewService;
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
@RequestMapping("/api/collections/{collectionId}")
@PreAuthorize("hasAuthority('/content/collections')")
public class CollectionSubmissionController {

    private final CollectionReviewService reviewService;

    public CollectionSubmissionController(CollectionReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/submit")
    public ReviewDtos.ReviewDetailResponse submit(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long collectionId,
            @Valid @RequestBody ReviewDtos.VersionRequest request,
            HttpServletRequest servletRequest
    ) {
        return reviewService.submit(userId(jwt), collectionId, request, clientIp(servletRequest));
    }

    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getClaimAsString("uid"));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
