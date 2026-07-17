package com.wedding.platform.content.project.web;

import com.wedding.platform.content.project.application.ProjectService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/projects")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('/content/projects')")
public class ProjectAdminController {

    private final ProjectService projectService;

    public ProjectAdminController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PutMapping("/{projectId}/creators")
    public ProjectDtos.ProjectResponse assignCreators(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectDtos.AssignProjectCreatorsRequest request,
            HttpServletRequest servletRequest
    ) {
        return projectService.assignCreators(userId(jwt), projectId, request, clientIp(servletRequest));
    }

    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getClaimAsString("uid"));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
