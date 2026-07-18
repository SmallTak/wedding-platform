package com.wedding.platform.content.publication.web;

import com.wedding.platform.content.publication.application.PublicCollectionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public")
public class PublicCollectionController {

    private final PublicCollectionService publicCollectionService;

    public PublicCollectionController(PublicCollectionService publicCollectionService) {
        this.publicCollectionService = publicCollectionService;
    }

    @GetMapping("/content/categories")
    public List<PublicCollectionDtos.CategorySummary> categories() {
        return publicCollectionService.categories();
    }

    @GetMapping("/collections")
    public PublicCollectionDtos.CollectionPage collections(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId
    ) {
        return publicCollectionService.collections(page, size, keyword, categoryId);
    }

    @GetMapping("/collections/{collectionId}")
    public PublicCollectionDtos.CollectionDetail collection(
            @PathVariable Long collectionId,
            @CookieValue(name = PublicAccessCookies.COOKIE_NAME, required = false) String accessToken
    ) {
        return publicCollectionService.collection(collectionId, accessToken);
    }

    @org.springframework.web.bind.annotation.PostMapping("/collections/{collectionId}/access")
    public ResponseEntity<PublicAccessDtos.AccessSessionResponse> access(
            @PathVariable Long collectionId,
            @Valid @org.springframework.web.bind.annotation.RequestBody PublicAccessDtos.AccessRequest accessRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        var session = publicCollectionService.unlock(
                collectionId,
                accessRequest,
                PublicAccessCookies.clientAddress(request)
        );
        PublicAccessCookies.write(
                request,
                response,
                "/api/public/collections/" + collectionId,
                session
        );
        return ResponseEntity.ok(new PublicAccessDtos.AccessSessionResponse(session.expiresAt()));
    }
}
