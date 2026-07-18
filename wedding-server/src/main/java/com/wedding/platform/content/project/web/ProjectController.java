package com.wedding.platform.content.project.web;

import com.wedding.platform.content.project.application.ProjectService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
@PreAuthorize("hasAuthority('/content/projects')")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectDtos.ProjectResponse createProject(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProjectDtos.CreateProjectRequest request,
            HttpServletRequest servletRequest
    ) {
        return projectService.createProject(userId(jwt), request, clientIp(servletRequest));
    }

    @GetMapping
    public ProjectDtos.ProjectPageResponse projects(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword
    ) {
        return projectService.listProjects(userId(jwt), page, size, keyword);
    }

    @GetMapping("/{projectId}")
    public ProjectDtos.ProjectResponse project(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long projectId
    ) {
        return projectService.getProject(userId(jwt), projectId);
    }

    @PutMapping("/{projectId}")
    public ProjectDtos.ProjectResponse updateProject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectDtos.UpdateProjectRequest request,
            HttpServletRequest servletRequest
    ) {
        return projectService.updateProject(userId(jwt), projectId, request, clientIp(servletRequest));
    }

    @DeleteMapping("/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long projectId,
            @RequestParam Long version,
            HttpServletRequest servletRequest
    ) {
        projectService.deleteProject(userId(jwt), projectId, version, clientIp(servletRequest));
    }

    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getClaimAsString("uid"));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
