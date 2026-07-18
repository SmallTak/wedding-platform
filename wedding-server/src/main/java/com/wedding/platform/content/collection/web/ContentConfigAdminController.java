package com.wedding.platform.content.collection.web;

import com.wedding.platform.content.collection.application.ContentConfigService;
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

import java.util.List;

@RestController
@RequestMapping("/api/admin/content")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('/config/content')")
public class ContentConfigAdminController {

    private final ContentConfigService contentConfigService;

    public ContentConfigAdminController(ContentConfigService contentConfigService) {
        this.contentConfigService = contentConfigService;
    }

    @GetMapping("/categories")
    public List<ContentConfigDtos.CategoryResponse> categories(@AuthenticationPrincipal Jwt jwt) {
        return contentConfigService.listCategories(userId(jwt));
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public ContentConfigDtos.CategoryResponse createCategory(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ContentConfigDtos.CreateCategoryRequest request,
            HttpServletRequest servletRequest
    ) {
        return contentConfigService.createCategory(userId(jwt), request, clientIp(servletRequest));
    }

    @PutMapping("/categories/{categoryId}")
    public ContentConfigDtos.CategoryResponse updateCategory(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long categoryId,
            @Valid @RequestBody ContentConfigDtos.UpdateCategoryRequest request,
            HttpServletRequest servletRequest
    ) {
        return contentConfigService.updateCategory(userId(jwt), categoryId, request, clientIp(servletRequest));
    }

    @DeleteMapping("/categories/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long categoryId,
            @RequestParam Long version,
            HttpServletRequest servletRequest
    ) {
        contentConfigService.deleteCategory(userId(jwt), categoryId, version, clientIp(servletRequest));
    }

    @GetMapping("/tags")
    public List<ContentConfigDtos.TagResponse> tags(@AuthenticationPrincipal Jwt jwt) {
        return contentConfigService.listTags(userId(jwt));
    }

    @PostMapping("/tags")
    @ResponseStatus(HttpStatus.CREATED)
    public ContentConfigDtos.TagResponse createTag(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ContentConfigDtos.CreateTagRequest request,
            HttpServletRequest servletRequest
    ) {
        return contentConfigService.createTag(userId(jwt), request, clientIp(servletRequest));
    }

    @PutMapping("/tags/{tagId}")
    public ContentConfigDtos.TagResponse updateTag(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long tagId,
            @Valid @RequestBody ContentConfigDtos.UpdateTagRequest request,
            HttpServletRequest servletRequest
    ) {
        return contentConfigService.updateTag(userId(jwt), tagId, request, clientIp(servletRequest));
    }

    @DeleteMapping("/tags/{tagId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTag(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long tagId,
            @RequestParam Long version,
            HttpServletRequest servletRequest
    ) {
        contentConfigService.deleteTag(userId(jwt), tagId, version, clientIp(servletRequest));
    }

    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getClaimAsString("uid"));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
