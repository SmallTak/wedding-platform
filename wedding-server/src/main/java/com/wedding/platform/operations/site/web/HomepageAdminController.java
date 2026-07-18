package com.wedding.platform.operations.site.web;

import com.wedding.platform.operations.site.application.HomepageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/site/home")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('/site/home')")
public class HomepageAdminController {

    private final HomepageService homepageService;

    public HomepageAdminController(HomepageService homepageService) {
        this.homepageService = homepageService;
    }

    @GetMapping
    public HomepageDtos.FeatureOptions options(@AuthenticationPrincipal Jwt jwt) {
        return homepageService.options(userId(jwt));
    }

    @PutMapping
    public HomepageDtos.FeatureOptions replace(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody HomepageDtos.ReplaceFeaturesRequest request,
            HttpServletRequest servletRequest
    ) {
        return homepageService.replace(userId(jwt), request, clientIp(servletRequest));
    }

    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getClaimAsString("uid"));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
