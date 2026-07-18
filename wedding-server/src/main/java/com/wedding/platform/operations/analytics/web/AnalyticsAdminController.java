package com.wedding.platform.operations.analytics.web;

import com.wedding.platform.operations.analytics.application.AnalyticsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/analytics")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('/analytics')")
public class AnalyticsAdminController {

    private final AnalyticsService analyticsService;

    public AnalyticsAdminController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/overview")
    public AnalyticsDtos.AnalyticsOverview overview(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "30") int days
    ) {
        return analyticsService.overview(
                Long.valueOf(jwt.getClaimAsString("uid")),
                days
        );
    }
}
