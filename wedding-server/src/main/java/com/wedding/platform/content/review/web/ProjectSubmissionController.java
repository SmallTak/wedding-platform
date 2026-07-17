package com.wedding.platform.content.review.web;

import com.wedding.platform.content.review.application.ProjectReviewService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}")
@PreAuthorize("hasAuthority('/content/projects')")
public class ProjectSubmissionController {

    private final ProjectReviewService reviewService;

    public ProjectSubmissionController(ProjectReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/submit")
    public ReviewDtos.ProjectReviewDetailResponse submit(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long projectId,
            @Valid @RequestBody ReviewDtos.VersionRequest request,
            HttpServletRequest servletRequest
    ) {
        return reviewService.submit(userId(jwt), projectId, request, clientIp(servletRequest));
    }

    @GetMapping("/review")
    public ReviewDtos.ProjectReviewDetailResponse review(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long projectId
    ) {
        return reviewService.detail(userId(jwt), projectId);
    }

    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getClaimAsString("uid"));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
