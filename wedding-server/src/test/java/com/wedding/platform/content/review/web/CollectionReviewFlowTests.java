package com.wedding.platform.content.review.web;

import com.jayway.jsonpath.JsonPath;
import com.wedding.platform.content.collection.persistence.entity.ContentCategory;
import com.wedding.platform.content.collection.persistence.entity.ContentTag;
import com.wedding.platform.content.collection.persistence.repository.ContentCategoryRepository;
import com.wedding.platform.content.collection.persistence.repository.ContentTagRepository;
import com.wedding.platform.content.collection.persistence.repository.WorkCollectionRepository;
import com.wedding.platform.content.media.persistence.entity.CollectionPhoto;
import com.wedding.platform.content.media.persistence.repository.CollectionPhotoRepository;
import com.wedding.platform.content.review.application.ReviewRevisionService;
import com.wedding.platform.content.shared.ContentVisibility;
import com.wedding.platform.content.shared.PublishStatus;
import com.wedding.platform.content.shared.ReviewStatus;
import jakarta.servlet.http.Cookie;
import com.wedding.platform.system.account.persistence.entity.SystemRole;
import com.wedding.platform.system.account.persistence.entity.SystemUser;
import com.wedding.platform.system.account.persistence.repository.SystemRoleRepository;
import com.wedding.platform.system.account.persistence.repository.SystemUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CollectionReviewFlowTests {

    private static final String PASSWORD = "Review@Test123";
    private static final String ADMIN_MOBILE = "13800000401";
    private static final String OWNER_MOBILE = "13900000401";
    private static final String OUTSIDER_MOBILE = "13900000402";
    private static final String ACCESS_PASSWORD = "Guest@Test123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SystemUserRepository userRepository;

    @Autowired
    private SystemRoleRepository roleRepository;

    @Autowired
    private ContentCategoryRepository categoryRepository;

    @Autowired
    private ContentTagRepository tagRepository;

    @Autowired
    private WorkCollectionRepository collectionRepository;

    @Autowired
    private CollectionPhotoRepository photoRepository;

    @Autowired
    private ReviewRevisionService reviewRevisionService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private SystemUser admin;
    private ContentCategory category;
    private ContentTag tag;

    @BeforeEach
    void createFixtures() {
        admin = ensureAccount(ADMIN_MOBILE, "ADMIN", "Review Admin");
        ensureAccount(OWNER_MOBILE, "CREATOR", "Review Owner");
        ensureAccount(OUTSIDER_MOBILE, "CREATOR", "Review Outsider");
        category = ensureCategory();
        tag = ensureTag();
    }

    @Test
    void collectionCanBeSubmittedReviewedPublishedAndTakenOffline() throws Exception {
        String adminToken = login(ADMIN_MOBILE);
        String ownerToken = login(OWNER_MOBILE);
        String outsiderToken = login(OUTSIDER_MOBILE);

        String collectionJson = mockMvc.perform(post("/api/collections")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": null,
                                  "title": "Published Review Story",
                                  "description": "A collection moving through the complete review workflow",
                                  "categoryId": %d,
                                  "tagIds": [%d]
                                }
                                """.formatted(category.getId(), tag.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number collectionId = JsonPath.read(collectionJson, "$.id");

        MockMultipartFile coverFile = new MockMultipartFile(
                "files",
                "cover.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                image(900, 600, new Color(90, 62, 44))
        );
        MockMultipartFile secondaryFile = new MockMultipartFile(
                "files",
                "secondary.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                image(600, 900, new Color(52, 74, 92))
        );
        String uploadJson = mockMvc.perform(multipart(
                                "/api/collections/{collectionId}/photos", collectionId.longValue())
                        .file(coverFile)
                        .file(secondaryFile)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photos[0].reviewStatus").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        Number uploadVersion = JsonPath.read(uploadJson, "$.collectionVersion");
        Number coverPhotoId = JsonPath.read(uploadJson, "$.photos[0].id");
        Number rejectedPhotoId = JsonPath.read(uploadJson, "$.photos[1].id");

        String coverJson = mockMvc.perform(put("/api/collections/{collectionId}/cover", collectionId.longValue())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "photoId": %d
                                }
                                """.formatted(uploadVersion.longValue(), coverPhotoId.longValue())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Number coverVersion = JsonPath.read(coverJson, "$.collectionVersion");

        mockMvc.perform(post("/api/collections/{collectionId}/submit", collectionId.longValue())
                        .header("Authorization", bearer(outsiderToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + coverVersion + "}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COLLECTION_ACCESS_DENIED"));

        String submitJson = mockMvc.perform(post(
                                "/api/collections/{collectionId}/submit", collectionId.longValue())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + coverVersion + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collection.reviewStatus").value("PENDING"))
                .andExpect(jsonPath("$.collection.publishStatus").value("UNPUBLISHED"))
                .andExpect(jsonPath("$.photoBatch.photos[0].reviewStatus").value("PENDING"))
                .andExpect(jsonPath("$.photoBatch.photos[1].reviewStatus").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        Number submitVersion = JsonPath.read(submitJson, "$.collection.version");
        List<Number> fieldIds = JsonPath.read(
                submitJson,
                "$.reviewHistory.currentItems[?(@.itemType == 'FIELD')].id"
        );

        String approvedFieldsJson = mockMvc.perform(put(
                                "/api/admin/reviews/collections/{collectionId}/fields",
                                collectionId.longValue())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "reviewItemIds": [%s],
                                  "decision": "APPROVE"
                                }
                                """.formatted(
                                submitVersion.longValue(),
                                fieldIds.stream().map(String::valueOf).collect(Collectors.joining(","))
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collection.reviewStatus").value("PENDING"))
                .andExpect(jsonPath(
                        "$.reviewHistory.currentItems[?(@.itemType == 'FIELD' && @.status == 'APPROVED')]",
                        hasSize(7)
                ))
                .andReturn().getResponse().getContentAsString();
        Number approvedFieldsVersion = JsonPath.read(approvedFieldsJson, "$.collection.version");

        mockMvc.perform(get("/api/admin/reviews/collections")
                        .header("Authorization", bearer(adminToken))
                        .param("reviewStatus", "PENDING")
                        .param("keyword", "Published Review Story"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].pendingFields").value(0))
                .andExpect(jsonPath("$.content[0].pendingPhotos").value(2));

        String approvedCoverJson = mockMvc.perform(put(
                                "/api/admin/reviews/collections/{collectionId}/photos",
                                collectionId.longValue())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "photoIds": [%d],
                                  "decision": "APPROVE"
                                }
                                """.formatted(approvedFieldsVersion.longValue(), coverPhotoId.longValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collection.reviewStatus").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        Number approvedCoverVersion = JsonPath.read(approvedCoverJson, "$.collection.version");

        String rejectedJson = mockMvc.perform(put(
                                "/api/admin/reviews/collections/{collectionId}/photos",
                                collectionId.longValue())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "photoIds": [%d],
                                  "decision": "REJECT",
                                  "reason": "The framing needs another selection"
                                }
                                """.formatted(approvedCoverVersion.longValue(), rejectedPhotoId.longValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collection.reviewStatus").value("PARTIALLY_REJECTED"))
                .andExpect(jsonPath("$.photoBatch.photos[1].reviewStatus").value("REJECTED"))
                .andExpect(jsonPath("$.photoBatch.photos[1].rejectionReason")
                        .value("The framing needs another selection"))
                .andReturn().getResponse().getContentAsString();
        Number rejectedVersion = JsonPath.read(rejectedJson, "$.collection.version");

        mockMvc.perform(get("/api/public/collections/{collectionId}", collectionId.longValue()))
                .andExpect(status().isNotFound());

        String deleteJson = mockMvc.perform(delete(
                                "/api/collections/{collectionId}/photos/{photoId}",
                                collectionId.longValue(),
                                rejectedPhotoId.longValue())
                        .header("Authorization", bearer(ownerToken))
                        .param("version", rejectedVersion.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photos.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        Number readyVersion = JsonPath.read(deleteJson, "$.collectionVersion");

        mockMvc.perform(get("/api/collections/{collectionId}", collectionId.longValue())
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("APPROVED"))
                .andExpect(jsonPath("$.publishStatus").value("READY"));

        String publishedJson = mockMvc.perform(post(
                                "/api/admin/reviews/collections/{collectionId}/publish",
                                collectionId.longValue())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "visibility": "PASSWORD",
                                  "accessPassword": "%s",
                                  "featured": true,
                                  "pinned": false,
                                  "sortOrder": 10
                                }
                                """.formatted(readyVersion.longValue(), ACCESS_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collection.publishStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.collection.visibility").value("PASSWORD"))
                .andReturn().getResponse().getContentAsString();
        Number publishedVersion = JsonPath.read(publishedJson, "$.collection.version");
        String storedPasswordHash = collectionRepository.findByIdAndDeletedFalse(collectionId.longValue())
                .orElseThrow()
                .getAccessPasswordHash();
        org.junit.jupiter.api.Assertions.assertNotEquals(ACCESS_PASSWORD, storedPasswordHash);
        org.junit.jupiter.api.Assertions.assertTrue(passwordEncoder.matches(ACCESS_PASSWORD, storedPasswordHash));

        mockMvc.perform(get("/api/public/collections")
                        .param("keyword", "Published Review Story"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/public/collections/{collectionId}", collectionId.longValue()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CONTENT_ACCESS_REQUIRED"));

        mockMvc.perform(post("/api/public/collections/{collectionId}/access", collectionId.longValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"Wrong@Test123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CONTENT_ACCESS_INVALID"));

        String accessCookieHeader = mockMvc.perform(post(
                                "/api/public/collections/{collectionId}/access",
                                collectionId.longValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + ACCESS_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        containsString("Path=/api/public/collections/" + collectionId.longValue())))
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);
        Cookie accessCookie = accessCookie(accessCookieHeader);

        mockMvc.perform(get("/api/public/collections/{collectionId}", collectionId.longValue())
                        .cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photos.length()").value(1))
                .andExpect(jsonPath("$.photos[0].id").value(coverPhotoId.longValue()))
                .andExpect(jsonPath("$.collection.coverOriginalUrl")
                        .value(org.hamcrest.Matchers.startsWith("/media/branded/")))
                .andExpect(jsonPath("$.photos[0].originalUrl")
                        .value(org.hamcrest.Matchers.startsWith("/media/branded/")))
                .andExpect(jsonPath("$.photos[0].previewUrl")
                        .value(org.hamcrest.Matchers.startsWith("/media/previews/")))
                .andExpect(jsonPath("$.photos[0].originalPath").doesNotExist());

        mockMvc.perform(delete("/api/collections/{collectionId}", collectionId.longValue())
                        .header("Authorization", bearer(ownerToken))
                        .param("version", publishedVersion.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COLLECTION_PUBLISHED_LOCKED"));

        mockMvc.perform(put("/api/collections/{collectionId}", collectionId.longValue())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "projectId": null,
                                  "title": "Locked Story",
                                  "description": "Should remain locked",
                                  "categoryId": %d,
                                  "tagIds": [%d]
                                }
                                """.formatted(publishedVersion.longValue(), category.getId(), tag.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COLLECTION_PUBLISHED_LOCKED"));

        String offlineJson = mockMvc.perform(post(
                                "/api/admin/reviews/collections/{collectionId}/offline",
                                collectionId.longValue())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "reason": "Seasonal homepage refresh"
                                }
                                """.formatted(publishedVersion.longValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collection.publishStatus").value("OFFLINE"))
                .andReturn().getResponse().getContentAsString();
        Number offlineVersion = JsonPath.read(offlineJson, "$.collection.version");

        mockMvc.perform(get("/api/public/collections/{collectionId}", collectionId.longValue())
                        .cookie(accessCookie))
                .andExpect(status().isNotFound());

        String unchangedOfflineJson = mockMvc.perform(put(
                                "/api/collections/{collectionId}", collectionId.longValue())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "projectId": null,
                                  "title": "Published Review Story",
                                  "description": "A collection moving through the complete review workflow",
                                  "categoryId": %d,
                                  "tagIds": [%d]
                                }
                                """.formatted(offlineVersion.longValue(), category.getId(), tag.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("APPROVED"))
                .andExpect(jsonPath("$.publishStatus").value("OFFLINE"))
                .andReturn().getResponse().getContentAsString();
        Number unchangedOfflineVersion = JsonPath.read(unchangedOfflineJson, "$.version");
        org.junit.jupiter.api.Assertions.assertEquals(
                offlineVersion.longValue(),
                unchangedOfflineVersion.longValue()
        );

        String republishedJson = mockMvc.perform(post(
                                "/api/admin/reviews/collections/{collectionId}/publish",
                                collectionId.longValue())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "visibility": "PUBLIC",
                                  "featured": false,
                                  "pinned": false,
                                  "sortOrder": 0
                                }
                                """.formatted(unchangedOfflineVersion.longValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collection.publishStatus").value("PUBLISHED"))
                .andReturn().getResponse().getContentAsString();
        Number republishedVersion = JsonPath.read(republishedJson, "$.collection.version");

        String secondOfflineJson = mockMvc.perform(post(
                                "/api/admin/reviews/collections/{collectionId}/offline",
                                collectionId.longValue())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "reason": "Prepare a reviewed content revision"
                                }
                                """.formatted(republishedVersion.longValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collection.publishStatus").value("OFFLINE"))
                .andReturn().getResponse().getContentAsString();
        Number secondOfflineVersion = JsonPath.read(secondOfflineJson, "$.collection.version");

        String updatedOfflineJson = mockMvc.perform(put(
                                "/api/collections/{collectionId}", collectionId.longValue())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "projectId": null,
                                  "title": "Offline Editable Story",
                                  "description": "Editing is available again after offline",
                                  "categoryId": %d,
                                  "tagIds": [%d]
                                }
                                """.formatted(secondOfflineVersion.longValue(), category.getId(), tag.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("DRAFT"))
                .andExpect(jsonPath("$.publishStatus").value("OFFLINE"))
                .andReturn().getResponse().getContentAsString();
        Number updatedOfflineVersion = JsonPath.read(updatedOfflineJson, "$.version");

        mockMvc.perform(post("/api/admin/reviews/collections/{collectionId}/publish", collectionId.longValue())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "visibility": "PUBLIC",
                                  "featured": false,
                                  "pinned": false,
                                  "sortOrder": 0
                                }
                                """.formatted(updatedOfflineVersion.longValue())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COLLECTION_NOT_READY"));

        String resubmitJson = mockMvc.perform(post(
                                "/api/collections/{collectionId}/submit", collectionId.longValue())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + updatedOfflineVersion + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewHistory.revisions.length()").value(2))
                .andExpect(jsonPath("$.reviewHistory.revisions[0].revisionNo").value(2))
                .andExpect(jsonPath("$.reviewHistory.revisions[0].items.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertNotNull(resubmitJson);

        mockMvc.perform(get("/api/admin/dashboard/overview")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishedCollections").isNumber());
    }

    @Test
    void approvedCreatorPhotoDeletionAndOrderChangesRequireAnotherReview() throws Exception {
        String adminToken = login(ADMIN_MOBILE);
        String ownerToken = login(OWNER_MOBILE);

        ApprovedOfflineCollection reordered = createApprovedOfflineCollection(
                ownerToken,
                "Approved Offline Reorder Story"
        );
        mockMvc.perform(put("/api/collections/{collectionId}/photos/order", reordered.id())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "photoIds": [%d, %d]
                                }
                                """.formatted(
                                reordered.version(),
                                reordered.firstPhotoId(),
                                reordered.secondPhotoId()
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collectionVersion").value(reordered.version()));

        String reorderedJson = mockMvc.perform(put(
                                "/api/collections/{collectionId}/photos/order", reordered.id())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "photoIds": [%d, %d]
                                }
                                """.formatted(
                                reordered.version(),
                                reordered.secondPhotoId(),
                                reordered.firstPhotoId()
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photos[0].reviewStatus").value("DRAFT"))
                .andExpect(jsonPath("$.photos[1].reviewStatus").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        Number reorderedVersion = JsonPath.read(reorderedJson, "$.collectionVersion");

        mockMvc.perform(get("/api/collections/{collectionId}", reordered.id())
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("DRAFT"))
                .andExpect(jsonPath("$.publishStatus").value("OFFLINE"));
        assertCollectionPublishRejected(adminToken, reordered.id(), reorderedVersion.longValue());

        ApprovedOfflineCollection deleted = createApprovedOfflineCollection(
                ownerToken,
                "Approved Offline Delete Story"
        );
        String deletedJson = mockMvc.perform(delete(
                                "/api/collections/{collectionId}/photos/{photoId}",
                                deleted.id(),
                                deleted.secondPhotoId())
                        .header("Authorization", bearer(ownerToken))
                        .param("version", String.valueOf(deleted.version())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photos.length()").value(1))
                .andExpect(jsonPath("$.photos[0].reviewStatus").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        Number deletedVersion = JsonPath.read(deletedJson, "$.collectionVersion");

        mockMvc.perform(get("/api/collections/{collectionId}", deleted.id())
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("DRAFT"))
                .andExpect(jsonPath("$.publishStatus").value("OFFLINE"));
        assertCollectionPublishRejected(adminToken, deleted.id(), deletedVersion.longValue());

        ApprovedOfflineCollection creatorsChanged = createApprovedOfflineCollection(
                ownerToken,
                "Approved Offline Creator Story"
        );
        SystemUser outsider = userRepository.findByMobileAndDeletedFalse(OUTSIDER_MOBILE).orElseThrow();
        String creatorsChangedJson = mockMvc.perform(put(
                                "/api/admin/collections/{collectionId}/creators", creatorsChanged.id())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "creatorUserIds": [%d]
                                }
                                """.formatted(creatorsChanged.version(), outsider.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("DRAFT"))
                .andExpect(jsonPath("$.publishStatus").value("OFFLINE"))
                .andReturn().getResponse().getContentAsString();
        Number creatorsChangedVersion = JsonPath.read(creatorsChangedJson, "$.version");
        assertCollectionPublishRejected(
                adminToken,
                creatorsChanged.id(),
                creatorsChangedVersion.longValue()
        );
    }

    private ApprovedOfflineCollection createApprovedOfflineCollection(
            String ownerToken,
            String title
    ) throws Exception {
        String collectionJson = mockMvc.perform(post("/api/collections")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": null,
                                  "title": "%s",
                                  "description": "Approved offline photo regression fixture",
                                  "categoryId": %d,
                                  "tagIds": [%d]
                                }
                                """.formatted(title, category.getId(), tag.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number collectionId = JsonPath.read(collectionJson, "$.id");

        String uploadJson = mockMvc.perform(multipart(
                                "/api/collections/{collectionId}/photos", collectionId.longValue())
                        .file(new MockMultipartFile(
                                "files",
                                "first.jpg",
                                MediaType.IMAGE_JPEG_VALUE,
                                image(640, 480, new Color(84, 62, 44))
                        ))
                        .file(new MockMultipartFile(
                                "files",
                                "second.jpg",
                                MediaType.IMAGE_JPEG_VALUE,
                                image(480, 640, new Color(48, 70, 92))
                        ))
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Number uploadVersion = JsonPath.read(uploadJson, "$.collectionVersion");
        Number firstPhotoId = JsonPath.read(uploadJson, "$.photos[0].id");
        Number secondPhotoId = JsonPath.read(uploadJson, "$.photos[1].id");

        mockMvc.perform(put("/api/collections/{collectionId}/cover", collectionId.longValue())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "photoId": %d
                                }
                                """.formatted(uploadVersion.longValue(), firstPhotoId.longValue())))
                .andExpect(status().isOk());

        List<CollectionPhoto> photos = photoRepository
                .findAllByCollectionIdAndDeletedFalseOrderBySortOrderAscIdAsc(collectionId.longValue());
        Instant reviewedAt = Instant.now();
        photos.forEach(photo -> {
            photo.setReviewStatus(ReviewStatus.APPROVED);
            photo.setReviewedAt(reviewedAt);
            photo.setReviewedBy(admin.getId());
        });
        photoRepository.saveAllAndFlush(photos);

        var collection = collectionRepository.findByIdAndDeletedFalse(collectionId.longValue()).orElseThrow();
        collection.setReviewStatus(ReviewStatus.APPROVED);
        collection.setPublishStatus(PublishStatus.OFFLINE);
        collection.setVisibility(ContentVisibility.PUBLIC);
        collection.setReviewedAt(reviewedAt);
        collection.setReviewedBy(admin.getId());
        collection.setPublishedAt(reviewedAt);
        collection.setPublishedBy(admin.getId());
        collection.setOfflineReason("Regression fixture");
        collection.setUpdatedBy(admin.getId());
        collection = collectionRepository.saveAndFlush(collection);
        reviewRevisionService.ensureCollectionBaseline(collection, photos);

        return new ApprovedOfflineCollection(
                collection.getId(),
                collection.getVersion(),
                firstPhotoId.longValue(),
                secondPhotoId.longValue()
        );
    }

    private void assertCollectionPublishRejected(
            String adminToken,
            long collectionId,
            long version
    ) throws Exception {
        mockMvc.perform(post("/api/admin/reviews/collections/{collectionId}/publish", collectionId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "visibility": "PUBLIC",
                                  "featured": false,
                                  "pinned": false,
                                  "sortOrder": 0
                                }
                                """.formatted(version)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COLLECTION_NOT_READY"));
    }

    private byte[] image(int width, int height, Color color) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(Color.WHITE);
            graphics.drawLine(0, 0, width - 1, height - 1);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        org.junit.jupiter.api.Assertions.assertTrue(ImageIO.write(image, "jpeg", output));
        return output.toByteArray();
    }

    private Cookie accessCookie(String setCookieHeader) {
        org.junit.jupiter.api.Assertions.assertNotNull(setCookieHeader);
        String nameValue = setCookieHeader.split(";", 2)[0];
        String[] parts = nameValue.split("=", 2);
        return new Cookie(parts[0], parts[1]);
    }

    private SystemUser ensureAccount(String mobile, String accountType, String displayName) {
        SystemUser user = userRepository.findByMobileAndDeletedFalse(mobile).orElseGet(() -> {
            SystemRole role = roleRepository.findByCodeAndStatus(accountType, "ACTIVE").orElseThrow();
            SystemUser created = new SystemUser();
            created.setMobile(mobile);
            created.setPasswordHash(passwordEncoder.encode(PASSWORD));
            created.setAccountType(accountType);
            created.setRoles(new HashSet<>(Set.of(role)));
            return created;
        });
        user.setDisplayName(displayName);
        user.setAccountStatus("ACTIVE");
        user.setMustChangePassword(false);
        user.setProfileCompleted(true);
        user.setDeleted(false);
        return userRepository.saveAndFlush(user);
    }

    private ContentCategory ensureCategory() {
        String name = "Review Test Category";
        ContentCategory value = categoryRepository.findAllByDeletedFalseOrderBySortOrderAscCreatedAtAsc().stream()
                .filter(item -> name.equals(item.getName()))
                .findFirst()
                .orElseGet(() -> {
                    ContentCategory created = new ContentCategory();
                    created.setName(name);
                    created.setCreatedBy(admin.getId());
                    return created;
                });
        value.setDescription("Review workflow integration test category");
        value.setSortOrder(120);
        value.setStatus("ACTIVE");
        value.setUpdatedBy(admin.getId());
        return categoryRepository.saveAndFlush(value);
    }

    private ContentTag ensureTag() {
        String name = "Review Test Tag";
        ContentTag value = tagRepository.findAllByDeletedFalseOrderBySortOrderAscCreatedAtAsc().stream()
                .filter(item -> name.equals(item.getName()))
                .findFirst()
                .orElseGet(() -> {
                    ContentTag created = new ContentTag();
                    created.setName(name);
                    created.setCreatedBy(admin.getId());
                    return created;
                });
        value.setSortOrder(120);
        value.setStatus("ACTIVE");
        value.setUpdatedBy(admin.getId());
        return tagRepository.saveAndFlush(value);
    }

    private String login(String mobile) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobile\":\"" + mobile + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record ApprovedOfflineCollection(
            long id,
            long version,
            long firstPhotoId,
            long secondPhotoId
    ) {
    }
}
