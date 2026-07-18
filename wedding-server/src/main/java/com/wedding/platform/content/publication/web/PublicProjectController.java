package com.wedding.platform.content.publication.web;

import com.wedding.platform.content.publication.application.PublicProjectService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/projects")
public class PublicProjectController {

    private final PublicProjectService publicProjectService;

    public PublicProjectController(PublicProjectService publicProjectService) {
        this.publicProjectService = publicProjectService;
    }

    @GetMapping
    public PublicProjectDtos.ProjectPage projects(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String regionCode
    ) {
        return publicProjectService.projects(page, size, keyword, regionCode);
    }

    @GetMapping("/{projectId}")
    public PublicProjectDtos.ProjectDetail project(
            @PathVariable Long projectId,
            @CookieValue(name = PublicAccessCookies.COOKIE_NAME, required = false) String accessToken
    ) {
        return publicProjectService.project(projectId, accessToken);
    }

    @PostMapping("/{projectId}/access")
    public ResponseEntity<PublicAccessDtos.AccessSessionResponse> access(
            @PathVariable Long projectId,
            @Valid @RequestBody PublicAccessDtos.AccessRequest accessRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        var session = publicProjectService.unlock(
                projectId,
                accessRequest,
                PublicAccessCookies.clientAddress(request)
        );
        PublicAccessCookies.write(
                request,
                response,
                "/api/public/projects/" + projectId,
                session
        );
        return ResponseEntity.ok(new PublicAccessDtos.AccessSessionResponse(session.expiresAt()));
    }
}
