package com.wedding.platform.content.collection.web;

import com.wedding.platform.content.collection.application.CollectionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/collections")
@PreAuthorize("hasAuthority('/content/collections')")
public class CollectionController {

    private final CollectionService collectionService;

    public CollectionController(CollectionService collectionService) {
        this.collectionService = collectionService;
    }

    @GetMapping("/options")
    public CollectionDtos.CollectionOptionsResponse options(@AuthenticationPrincipal Jwt jwt) {
        return collectionService.options(userId(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CollectionDtos.CollectionResponse createCollection(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CollectionDtos.CreateCollectionRequest request,
            HttpServletRequest servletRequest
    ) {
        return collectionService.createCollection(userId(jwt), request, clientIp(servletRequest));
    }

    @GetMapping
    public CollectionDtos.CollectionPageResponse collections(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long categoryId
    ) {
        return collectionService.listCollections(userId(jwt), page, size, keyword, projectId, categoryId);
    }

    @GetMapping("/{collectionId}")
    public CollectionDtos.CollectionResponse collection(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long collectionId
    ) {
        return collectionService.getCollection(userId(jwt), collectionId);
    }

    @PutMapping("/{collectionId}")
    public CollectionDtos.CollectionResponse updateCollection(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long collectionId,
            @Valid @RequestBody CollectionDtos.UpdateCollectionRequest request,
            HttpServletRequest servletRequest
    ) {
        return collectionService.updateCollection(
                userId(jwt), collectionId, request, clientIp(servletRequest));
    }

    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getClaimAsString("uid"));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
