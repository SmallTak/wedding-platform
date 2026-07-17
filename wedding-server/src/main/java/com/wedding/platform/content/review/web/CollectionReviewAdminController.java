package com.wedding.platform.content.review.web;

import com.wedding.platform.content.review.application.CollectionReviewService;
import com.wedding.platform.content.shared.PublishStatus;
import com.wedding.platform.content.shared.ReviewStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reviews/collections")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('/review/collections')")
public class CollectionReviewAdminController {

    private final CollectionReviewService reviewService;

    public CollectionReviewAdminController(CollectionReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public ReviewDtos.ReviewQueueResponse collections(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ReviewStatus reviewStatus,
            @RequestParam(required = false) PublishStatus publishStatus
    ) {
        return reviewService.list(userId(jwt), page, size, keyword, reviewStatus, publishStatus);
    }

    @GetMapping("/{collectionId}")
    public ReviewDtos.ReviewDetailResponse detail(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long collectionId
    ) {
        return reviewService.detail(userId(jwt), collectionId);
    }

    @PutMapping("/{collectionId}/photos")
    public ReviewDtos.ReviewDetailResponse reviewPhotos(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long collectionId,
            @Valid @RequestBody ReviewDtos.ReviewPhotosRequest request,
            HttpServletRequest servletRequest
    ) {
        return reviewService.reviewPhotos(userId(jwt), collectionId, request, clientIp(servletRequest));
    }

    @PutMapping("/{collectionId}/fields")
    public ReviewDtos.ReviewDetailResponse reviewFields(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long collectionId,
            @Valid @RequestBody ReviewDtos.ReviewFieldsRequest request,
            HttpServletRequest servletRequest
    ) {
        return reviewService.reviewFields(userId(jwt), collectionId, request, clientIp(servletRequest));
    }

    @PostMapping("/{collectionId}/approve")
    public ReviewDtos.ReviewDetailResponse approve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long collectionId,
            @Valid @RequestBody ReviewDtos.VersionRequest request,
            HttpServletRequest servletRequest
    ) {
        return reviewService.approveCollection(userId(jwt), collectionId, request, clientIp(servletRequest));
    }

    @PostMapping("/{collectionId}/reject")
    public ReviewDtos.ReviewDetailResponse reject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long collectionId,
            @Valid @RequestBody ReviewDtos.RejectCollectionRequest request,
            HttpServletRequest servletRequest
    ) {
        return reviewService.rejectCollection(userId(jwt), collectionId, request, clientIp(servletRequest));
    }

    @PostMapping("/{collectionId}/publish")
    public ReviewDtos.ReviewDetailResponse publish(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long collectionId,
            @Valid @RequestBody ReviewDtos.PublishCollectionRequest request,
            HttpServletRequest servletRequest
    ) {
        return reviewService.publish(userId(jwt), collectionId, request, clientIp(servletRequest));
    }

    @PostMapping("/{collectionId}/offline")
    public ReviewDtos.ReviewDetailResponse offline(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long collectionId,
            @Valid @RequestBody ReviewDtos.OfflineCollectionRequest request,
            HttpServletRequest servletRequest
    ) {
        return reviewService.offline(userId(jwt), collectionId, request, clientIp(servletRequest));
    }

    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getClaimAsString("uid"));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
