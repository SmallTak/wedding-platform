package com.wedding.platform.content.project.web;

import com.wedding.platform.content.project.application.CustomerProjectService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/customer-project-applications")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('/accounts/customers')")
public class CustomerProjectAdminController {

    private final CustomerProjectService customerProjectService;

    public CustomerProjectAdminController(CustomerProjectService customerProjectService) {
        this.customerProjectService = customerProjectService;
    }

    @GetMapping
    public CustomerProjectDtos.ApplicationPage applications(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status
    ) {
        return customerProjectService.adminApplications(userId(jwt), page, size, status);
    }

    @PostMapping("/{applicationId}/approve")
    public CustomerProjectDtos.ApplicationResponse approve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long applicationId,
            @Valid @RequestBody CustomerProjectDtos.ReviewRequest request,
            HttpServletRequest servletRequest
    ) {
        return customerProjectService.approve(
                userId(jwt),
                applicationId,
                request,
                clientIp(servletRequest)
        );
    }

    @PostMapping("/{applicationId}/reject")
    public CustomerProjectDtos.ApplicationResponse reject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long applicationId,
            @Valid @RequestBody CustomerProjectDtos.ReviewRequest request,
            HttpServletRequest servletRequest
    ) {
        return customerProjectService.reject(
                userId(jwt),
                applicationId,
                request,
                clientIp(servletRequest)
        );
    }

    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getClaimAsString("uid"));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
