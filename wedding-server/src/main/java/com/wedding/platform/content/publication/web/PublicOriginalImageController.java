package com.wedding.platform.content.publication.web;

import com.wedding.platform.content.collection.persistence.entity.WorkCollection;
import com.wedding.platform.content.collection.persistence.repository.WorkCollectionRepository;
import com.wedding.platform.content.media.persistence.entity.CollectionPhoto;
import com.wedding.platform.content.media.persistence.entity.MediaAsset;
import com.wedding.platform.content.media.persistence.repository.CollectionPhotoRepository;
import com.wedding.platform.content.media.persistence.repository.MediaAssetRepository;
import com.wedding.platform.content.publication.application.PublicContentAccessService;
import com.wedding.platform.content.shared.ContentVisibility;
import com.wedding.platform.content.shared.PublishStatus;
import com.wedding.platform.content.shared.ReviewStatus;
import com.wedding.platform.platform.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/public/images")
public class PublicOriginalImageController {

    private static final Set<String> ALLOWED_REFERER_HOSTS = Set.of(
            "localhost",
            "127.0.0.1",
            "photo.shop-hz.top",
            "www.photo.shop-hz.top"
    );

    private final Path storageRoot;
    private final CollectionPhotoRepository photoRepository;
    private final MediaAssetRepository assetRepository;
    private final WorkCollectionRepository collectionRepository;
    private final PublicContentAccessService contentAccessService;

    public PublicOriginalImageController(
            @Value("${app.storage.root}") String storageRoot,
            CollectionPhotoRepository photoRepository,
            MediaAssetRepository assetRepository,
            WorkCollectionRepository collectionRepository,
            PublicContentAccessService contentAccessService
    ) {
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.photoRepository = photoRepository;
        this.assetRepository = assetRepository;
        this.collectionRepository = collectionRepository;
        this.contentAccessService = contentAccessService;
    }

    @GetMapping("/photos/{photoId}/original")
    public ResponseEntity<Resource> original(
            @PathVariable Long photoId,
            @CookieValue(name = PublicAccessCookies.COOKIE_NAME, required = false) String accessToken,
            HttpServletRequest request
    ) {
        requireAllowedReferer(request);
        CollectionPhoto photo = photoRepository.findById(photoId)
                .filter(item -> !Boolean.TRUE.equals(item.getDeleted()))
                .filter(item -> ReviewStatus.APPROVED == item.getReviewStatus())
                .orElseThrow(() -> notFound());
        WorkCollection collection = collectionRepository
                .findByIdAndDeletedFalseAndPublishStatus(photo.getCollectionId(), PublishStatus.PUBLISHED)
                .orElseThrow(() -> notFound());
        requireAccess(collection, accessToken);
        MediaAsset asset = assetRepository.findById(photo.getAssetId())
                .filter(item -> !Boolean.TRUE.equals(item.getDeleted()))
                .filter(item -> "SUCCESS".equals(item.getProcessStatus()))
                .orElseThrow(() -> notFound());
        Resource resource = originalResource(asset.getOriginalPath());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header(HttpHeaders.VARY, "Referer")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(contentType(asset.getMimeType()))
                .body(resource);
    }

    private void requireAllowedReferer(HttpServletRequest request) {
        String referer = request.getHeader(HttpHeaders.REFERER);
        if (referer == null || referer.isBlank()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "IMAGE_REFERER_REQUIRED",
                    "Original image access requires a website referer");
        }
        try {
            URI uri = UriComponentsBuilder.fromUriString(referer.trim()).build().toUri();
            String host = uri.getHost();
            if (host == null || !ALLOWED_REFERER_HOSTS.contains(host.toLowerCase())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "IMAGE_REFERER_INVALID",
                        "Original image access is restricted to the official website");
            }
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.FORBIDDEN, "IMAGE_REFERER_INVALID",
                    "Original image access is restricted to the official website");
        }
    }

    private void requireAccess(WorkCollection collection, String accessToken) {
        if (ContentVisibility.PUBLIC == collection.getVisibility()) {
            return;
        }
        if (ContentVisibility.PASSWORD == collection.getVisibility()
                && contentAccessService.isValid(
                        accessToken,
                        PublicContentAccessService.ContentType.COLLECTION,
                        collection.getId(),
                        collection.getVersion())) {
            return;
        }
        throw notFound();
    }

    private Resource originalResource(String originalPath) {
        Path relative = Path.of(originalPath).normalize();
        if (relative.isAbsolute() || relative.startsWith("..") || relative.toString().isBlank()) {
            throw notFound();
        }
        Path target = storageRoot.resolve(relative).normalize();
        if (!target.startsWith(storageRoot)) {
            throw notFound();
        }
        try {
            Resource resource = new UrlResource(target.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw notFound();
            }
            return resource;
        } catch (MalformedURLException exception) {
            throw notFound();
        }
    }

    private MediaType contentType(String mimeType) {
        if (MediaType.IMAGE_PNG_VALUE.equals(mimeType)) {
            return MediaType.IMAGE_PNG;
        }
        return MediaType.IMAGE_JPEG;
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PUBLIC_IMAGE_NOT_FOUND", "Original image was not found");
    }
}
