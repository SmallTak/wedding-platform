package com.wedding.platform.content.media.web;

import com.wedding.platform.content.media.application.CollectionPhotoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/collections/{collectionId}")
@PreAuthorize("hasAuthority('/content/collections')")
public class CollectionPhotoController {

    private final CollectionPhotoService photoService;

    public CollectionPhotoController(CollectionPhotoService photoService) {
        this.photoService = photoService;
    }

    @GetMapping("/photos")
    public PhotoDtos.PhotoBatchResponse photos(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long collectionId
    ) {
        return photoService.listPhotos(userId(jwt), collectionId);
    }

    @PostMapping(path = "/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PhotoDtos.PhotoBatchResponse upload(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long collectionId,
            @RequestParam("files") List<MultipartFile> files,
            HttpServletRequest servletRequest
    ) {
        return photoService.upload(userId(jwt), collectionId, files, clientIp(servletRequest));
    }

    @PutMapping("/photos/order")
    public PhotoDtos.PhotoBatchResponse reorder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long collectionId,
            @Valid @RequestBody PhotoDtos.ReorderPhotosRequest request,
            HttpServletRequest servletRequest
    ) {
        return photoService.reorder(userId(jwt), collectionId, request, clientIp(servletRequest));
    }

    @PutMapping("/cover")
    public PhotoDtos.PhotoBatchResponse setCover(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long collectionId,
            @Valid @RequestBody PhotoDtos.SetCoverRequest request,
            HttpServletRequest servletRequest
    ) {
        return photoService.setCover(userId(jwt), collectionId, request, clientIp(servletRequest));
    }

    @DeleteMapping("/photos/{photoId}")
    public PhotoDtos.PhotoBatchResponse deletePhoto(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long collectionId,
            @PathVariable Long photoId,
            @RequestParam Long version,
            HttpServletRequest servletRequest
    ) {
        return photoService.deletePhoto(
                userId(jwt), collectionId, photoId, version, clientIp(servletRequest));
    }

    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getClaimAsString("uid"));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
