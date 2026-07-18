package com.wedding.platform.content.project.web;

import com.wedding.platform.content.project.application.CustomerProjectService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customer/projects")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerProjectController {

    private final CustomerProjectService customerProjectService;

    public CustomerProjectController(CustomerProjectService customerProjectService) {
        this.customerProjectService = customerProjectService;
    }

    @GetMapping("/applications")
    public List<CustomerProjectDtos.ApplicationResponse> applications(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return customerProjectService.customerApplications(userId(jwt));
    }

    @PostMapping("/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerProjectDtos.ApplicationResponse apply(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CustomerProjectDtos.ApplyRequest request,
            HttpServletRequest servletRequest
    ) {
        return customerProjectService.apply(userId(jwt), request, clientIp(servletRequest));
    }

    @GetMapping("/linked")
    public List<CustomerProjectDtos.ApplicationResponse> linked(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return customerProjectService.linkedProjects(userId(jwt));
    }

    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getClaimAsString("uid"));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
