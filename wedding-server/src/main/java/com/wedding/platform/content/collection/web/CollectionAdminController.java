package com.wedding.platform.content.collection.web;

import com.wedding.platform.content.collection.application.CollectionService;
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
@RequestMapping("/api/admin/collections")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('/content/collections')")
public class CollectionAdminController {

    private final CollectionService collectionService;

    public CollectionAdminController(CollectionService collectionService) {
        this.collectionService = collectionService;
    }

    @PutMapping("/{collectionId}/creators")
    public CollectionDtos.CollectionResponse assignCreators(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long collectionId,
            @Valid @RequestBody CollectionDtos.AssignCollectionCreatorsRequest request,
            HttpServletRequest servletRequest
    ) {
        return collectionService.assignCreators(userId(jwt), collectionId, request, clientIp(servletRequest));
    }

    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getClaimAsString("uid"));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
