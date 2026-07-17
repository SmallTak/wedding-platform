package com.wedding.platform.content.review.web;

import com.wedding.platform.content.review.application.ProjectReviewService;
import com.wedding.platform.content.shared.PublishStatus;
import com.wedding.platform.content.shared.ReviewStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reviews/projects")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('/review/collections')")
public class ProjectReviewAdminController {

    private final ProjectReviewService reviewService;

    public ProjectReviewAdminController(ProjectReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public ReviewDtos.ProjectReviewQueueResponse projects(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ReviewStatus reviewStatus,
            @RequestParam(required = false) PublishStatus publishStatus
    ) {
        return reviewService.list(userId(jwt), page, size, keyword, reviewStatus, publishStatus);
    }

    @GetMapping("/{projectId}")
    public ReviewDtos.ProjectReviewDetailResponse detail(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long projectId
    ) {
        return reviewService.detail(userId(jwt), projectId);
    }

    @PutMapping("/{projectId}/fields")
    public ReviewDtos.ProjectReviewDetailResponse reviewFields(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long projectId,
            @Valid @RequestBody ReviewDtos.ReviewFieldsRequest request,
            HttpServletRequest servletRequest
    ) {
        return reviewService.reviewFields(userId(jwt), projectId, request, clientIp(servletRequest));
    }

    @PostMapping("/{projectId}/approve")
    public ReviewDtos.ProjectReviewDetailResponse approve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long projectId,
            @Valid @RequestBody ReviewDtos.VersionRequest request,
            HttpServletRequest servletRequest
    ) {
        return reviewService.approve(userId(jwt), projectId, request, clientIp(servletRequest));
    }

    @PostMapping("/{projectId}/reject")
    public ReviewDtos.ProjectReviewDetailResponse reject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long projectId,
            @Valid @RequestBody ReviewDtos.RejectCollectionRequest request,
            HttpServletRequest servletRequest
    ) {
        return reviewService.reject(userId(jwt), projectId, request, clientIp(servletRequest));
    }

    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getClaimAsString("uid"));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
