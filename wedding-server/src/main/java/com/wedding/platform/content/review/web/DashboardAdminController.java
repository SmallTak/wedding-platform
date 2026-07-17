package com.wedding.platform.content.review.web;

import com.wedding.platform.content.review.application.CollectionReviewService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('/dashboard')")
public class DashboardAdminController {

    private final CollectionReviewService reviewService;

    public DashboardAdminController(CollectionReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/overview")
    public ReviewDtos.DashboardOverviewResponse overview(@AuthenticationPrincipal Jwt jwt) {
        return reviewService.dashboard(Long.valueOf(jwt.getClaimAsString("uid")));
    }
}
