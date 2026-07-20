package com.wedding.platform.operations.web;

import com.jayway.jsonpath.JsonPath;
import com.wedding.platform.content.collection.persistence.entity.ContentCategory;
import com.wedding.platform.content.collection.persistence.entity.WorkCollection;
import com.wedding.platform.content.collection.persistence.repository.ContentCategoryRepository;
import com.wedding.platform.content.collection.persistence.repository.WorkCollectionRepository;
import com.wedding.platform.content.media.persistence.entity.CollectionPhoto;
import com.wedding.platform.content.media.persistence.entity.MediaAsset;
import com.wedding.platform.content.media.persistence.repository.CollectionPhotoRepository;
import com.wedding.platform.content.media.persistence.repository.MediaAssetRepository;
import com.wedding.platform.content.shared.ContentVisibility;
import com.wedding.platform.content.shared.PublishStatus;
import com.wedding.platform.content.shared.ReviewStatus;
import com.wedding.platform.operations.site.persistence.repository.HomepageCarouselItemRepository;
import com.wedding.platform.operations.site.persistence.repository.HomepageFeatureRepository;
import com.wedding.platform.operations.inquiry.persistence.repository.ConsultationLeadRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OperationsFlowTests {

    private static final String PASSWORD = "Operations@Test123";
    private static final String ADMIN_MOBILE = "13800000801";
    private static final String CREATOR_MOBILE = "13900000801";
    private static final String OUTSIDER_MOBILE = "13900000802";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SystemUserRepository userRepository;

    @Autowired
    private SystemRoleRepository roleRepository;

    @Autowired
    private ContentCategoryRepository categoryRepository;

    @Autowired
    private WorkCollectionRepository collectionRepository;

    @Autowired
    private MediaAssetRepository assetRepository;

    @Autowired
    private CollectionPhotoRepository photoRepository;

    @Autowired
    private HomepageFeatureRepository homepageFeatureRepository;

    @Autowired
    private HomepageCarouselItemRepository homepageCarouselItemRepository;

    @Autowired
    private ConsultationLeadRepository consultationLeadRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private SystemUser admin;
    private SystemUser creator;
    private SystemUser outsider;
    private WorkCollection collection;

    @BeforeEach
    void createFixtures() {
        admin = ensureAccount(ADMIN_MOBILE, "ADMIN", "Operations Admin");
        creator = ensureAccount(CREATOR_MOBILE, "CREATOR", "Reviewed Creator");
        outsider = ensureAccount(OUTSIDER_MOBILE, "CREATOR", "Outside Creator");
        collection = ensurePublicCollection();
    }

    @Test
    void feedbackSupportsReviewPublicationReplyAndHomepageCuration() throws Exception {
        String adminToken = login(ADMIN_MOBILE);
        String creatorToken = login(CREATOR_MOBILE);
        String outsiderToken = login(OUTSIDER_MOBILE);

        mockMvc.perform(post("/api/feedback")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(feedbackRequest(collection.getId(), outsider.getId(), null, "不应创建")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FEEDBACK_CREATOR_NOT_IN_COLLECTION"));

        String createdJson = mockMvc.perform(post("/api/feedback")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(feedbackRequest(collection.getId(), creator.getId(), null, "记录了很多真诚瞬间")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewStatus").value("PENDING"))
                .andExpect(jsonPath("$.publishStatus").value("UNPUBLISHED"))
                .andReturn().getResponse().getContentAsString();
        Number feedbackId = JsonPath.read(createdJson, "$.id");
        Number feedbackVersion = JsonPath.read(createdJson, "$.version");

        String approvedJson = mockMvc.perform(post(
                                "/api/admin/feedback/{feedbackId}/approve", feedbackId.longValue())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + feedbackVersion.longValue() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("APPROVED"))
                .andExpect(jsonPath("$.publishStatus").value("PUBLISHED"))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(get("/api/public/feedback"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(feedbackId.longValue()))
                .andExpect(jsonPath("$.content[0].customerDisplayName").value("林**"))
                .andExpect(jsonPath("$.content[0].content").value("记录了很多真诚瞬间"));

        mockMvc.perform(put(
                                "/api/feedback/{feedbackId}/reply", feedbackId.longValue())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"谢谢你们把信任交给我们\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply.reviewStatus").value("APPROVED"));

        mockMvc.perform(get("/api/public/feedback"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == %d)].reply.content".formatted(feedbackId.longValue()))
                        .value("谢谢你们把信任交给我们"));

        mockMvc.perform(put("/api/admin/site/home")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {"targetType":"FEEDBACK","targetId":%d,"sortOrder":10,"pinned":false}
                                  ]
                                }
                                """.formatted(feedbackId.longValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features.length()").value(1));

        mockMvc.perform(get("/api/public/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedback[0].id").value(feedbackId.longValue()));

        Number approvedVersion = JsonPath.read(approvedJson, "$.version");
        String offlineJson = mockMvc.perform(post("/api/admin/feedback/{feedbackId}/offline", feedbackId.longValue())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":%d,"reason":"客户要求暂时隐藏"}
                                """.formatted(approvedVersion.longValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishStatus").value("OFFLINE"))
                .andReturn().getResponse().getContentAsString();
        Number offlineVersion = JsonPath.read(offlineJson, "$.version");

        mockMvc.perform(get("/api/public/feedback"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == %d)]".formatted(feedbackId.longValue())).doesNotExist());

        mockMvc.perform(delete("/api/feedback/{feedbackId}", feedbackId.longValue())
                        .header("Authorization", bearer(adminToken))
                        .param("version", offlineVersion.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FEEDBACK_WITHDRAW_LOCKED"));
    }

    @Test
    void feedbackCanBeEditedOrWithdrawnOnlyByOriginalSubmitterBeforePublication() throws Exception {
        String adminToken = login(ADMIN_MOBILE);
        String creatorToken = login(CREATOR_MOBILE);

        String createdJson = mockMvc.perform(post("/api/feedback")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(feedbackRequest(collection.getId(), creator.getId(), null, "管理员代提交的评价")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number feedbackId = JsonPath.read(createdJson, "$.id");
        Number version = JsonPath.read(createdJson, "$.version");

        mockMvc.perform(put("/api/feedback/{feedbackId}", feedbackId.longValue())
                        .header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(feedbackRequest(collection.getId(), creator.getId(), version.longValue(), "不应修改")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FEEDBACK_ACCESS_DENIED"));

        mockMvc.perform(delete("/api/feedback/{feedbackId}", feedbackId.longValue())
                        .header("Authorization", bearer(creatorToken))
                        .param("version", version.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FEEDBACK_ACCESS_DENIED"));

        mockMvc.perform(delete("/api/feedback/{feedbackId}", feedbackId.longValue())
                        .header("Authorization", bearer(adminToken))
                        .param("version", version.toString()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/feedback")
                        .header("Authorization", bearer(adminToken))
                        .param("collectionId", collection.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == %d)]".formatted(feedbackId.longValue())).doesNotExist());
    }

    @Test
    void publicInquiryCanBeFollowedOnlyByAdministrator() throws Exception {
        String adminToken = login(ADMIN_MOBILE);
        String creatorToken = login(CREATOR_MOBILE);

        String receiptJson = mockMvc.perform(post("/api/public/inquiries")
                        .header("X-Forwarded-For", "198.51.100.80")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "周女士",
                                  "contact": "微信 tangshi-customer",
                                  "weddingDate": "2026-10-18",
                                  "region": "杭州",
                                  "serviceNeeds": "婚礼摄影与跟拍",
                                  "remark": "希望了解全天服务"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.referenceCode").value(org.hamcrest.Matchers.startsWith("INQ")))
                .andReturn().getResponse().getContentAsString();
        String referenceCode = JsonPath.read(receiptJson, "$.referenceCode");

        mockMvc.perform(get("/api/admin/inquiries")
                        .header("Authorization", bearer(creatorToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/site/home")
                        .header("Authorization", bearer(creatorToken)))
                .andExpect(status().isForbidden());

        String listJson = mockMvc.perform(get("/api/admin/inquiries")
                        .header("Authorization", bearer(adminToken))
                        .param("keyword", referenceCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].followStatus").value("NEW"))
                .andReturn().getResponse().getContentAsString();
        Number leadId = JsonPath.read(listJson, "$.content[0].id");
        Number version = JsonPath.read(listJson, "$.content[0].version");

        mockMvc.perform(put("/api/admin/inquiries/{leadId}", leadId.longValue())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "followStatus": "CONTACTED",
                                  "followNote": "已通过微信联系"
                                }
                                """.formatted(version.longValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followStatus").value("CONTACTED"))
                .andExpect(jsonPath("$.assignedAdminId").value(admin.getId()));

    }

    @Test
    void publicInquiryHoneypotDoesNotPersistAndRateLimitUsesTrustedRealIp() throws Exception {
        long existingLeads = consultationLeadRepository.count();
        mockMvc.perform(post("/api/public/inquiries")
                        .header("X-Real-IP", "198.51.100.90")
                        .header("X-Forwarded-For", "203.0.113.10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Bot",
                                  "contact": "bot@example.com",
                                  "serviceNeeds": "Automated submission",
                                  "website": "https://spam.example"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.referenceCode").value("RECEIVED"));
        org.junit.jupiter.api.Assertions.assertEquals(existingLeads, consultationLeadRepository.count());

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/public/inquiries")
                            .header("X-Real-IP", "198.51.100.91")
                            .header("X-Forwarded-For", "203.0.113." + (20 + attempt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "限流测试",
                                      "contact": "wechat-rate-limit",
                                      "serviceNeeds": "婚礼摄影咨询"
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.referenceCode")
                            .value(org.hamcrest.Matchers.startsWith("INQ")));
        }

        mockMvc.perform(post("/api/public/inquiries")
                        .header("X-Real-IP", "198.51.100.91")
                        .header("X-Forwarded-For", "203.0.113.99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "限流测试",
                                  "contact": "wechat-rate-limit",
                                  "serviceNeeds": "婚礼摄影咨询"
                                }
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("INQUIRY_RATE_LIMITED"));
    }

    @Test
    void homepageCarouselUsesPublishedPublicCollectionsWithValidCovers() throws Exception {
        String adminToken = login(ADMIN_MOBILE);
        String creatorToken = login(CREATOR_MOBILE);
        CarouselFixture fixture = ensureCarouselFixture();

        mockMvc.perform(get("/api/admin/site/home/carousel")
                        .header("Authorization", bearer(creatorToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/site/home/carousel")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates[?(@.collectionId == %d)]"
                        .formatted(fixture.collection().getId())).exists())
                .andExpect(jsonPath("$.candidates[?(@.collectionId == %d)].locationText"
                        .formatted(fixture.collection().getId())).value("杭州"));

        mockMvc.perform(put("/api/admin/site/home/carousel")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {"photoId":%d,"sortOrder":10,"focalX":25.5,"focalY":60},
                                    {"photoId":%d,"sortOrder":20,"focalX":50,"focalY":50}
                                  ]
                                }
                                """.formatted(fixture.photo().getId(), fixture.photo().getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HOMEPAGE_CAROUSEL_DUPLICATE"));

        mockMvc.perform(put("/api/admin/site/home/carousel")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {"photoId":%d,"sortOrder":10,"focalX":25.5,"focalY":60}
                                  ]
                                }
                                """.formatted(fixture.photo().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].photoId").value(fixture.photo().getId()))
                .andExpect(jsonPath("$.items[0].collectionId").value(fixture.collection().getId()))
                .andExpect(jsonPath("$.items[0].focalX").value(25.5))
                .andExpect(jsonPath("$.items[0].focalY").value(60))
                .andExpect(jsonPath("$.items[0].valid").value(true));

        mockMvc.perform(get("/api/public/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.carousel.length()").value(1))
                .andExpect(jsonPath("$.carousel[0].collectionId").value(fixture.collection().getId()))
                .andExpect(jsonPath("$.carousel[0].collectionTitle").value("Operations Carousel Collection"))
                .andExpect(jsonPath("$.carousel[0].description")
                        .value("Homepage carousel integration test collection"))
                .andExpect(jsonPath("$.carousel[0].eventDate").value("2026-10-18"))
                .andExpect(jsonPath("$.carousel[0].locationText").value("杭州"))
                .andExpect(jsonPath("$.carousel[0].originalUrl")
                        .value("/api/public/images/photos/" + fixture.photo().getId() + "/original"))
                .andExpect(jsonPath("$.carousel[0].previewUrl").value("/media/previews/operations-carousel.jpg"));

        fixture.collection().setVisibility(ContentVisibility.HIDDEN);
        collectionRepository.saveAndFlush(fixture.collection());

        mockMvc.perform(get("/api/public/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.carousel.length()").value(0));

        mockMvc.perform(get("/api/admin/site/home/carousel")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].valid").value(false))
                .andExpect(jsonPath("$.items[0].invalidReason").value("COLLECTION_NOT_PUBLIC"));

        WorkCollection restoredCollection = collectionRepository.findById(fixture.collection().getId()).orElseThrow();
        restoredCollection.setVisibility(ContentVisibility.PUBLIC);
        collectionRepository.saveAndFlush(restoredCollection);

        restoredCollection = collectionRepository.findById(fixture.collection().getId()).orElseThrow();
        restoredCollection.setCoverPhotoId(null);
        collectionRepository.saveAndFlush(restoredCollection);

        mockMvc.perform(get("/api/public/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.carousel.length()").value(0));

        mockMvc.perform(get("/api/admin/site/home/carousel")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates[?(@.collectionId == %d)]"
                        .formatted(fixture.collection().getId())).doesNotExist())
                .andExpect(jsonPath("$.items[0].invalidReason").value("COLLECTION_COVER_REQUIRED"));

        restoredCollection = collectionRepository.findById(fixture.collection().getId()).orElseThrow();
        restoredCollection.setCoverPhotoId(fixture.photo().getId());
        collectionRepository.saveAndFlush(restoredCollection);
        org.junit.jupiter.api.Assertions.assertEquals(1, homepageCarouselItemRepository.count());
    }

    private String feedbackRequest(Long collectionId, Long creatorId, Long version, String content) {
        String versionField = version == null ? "" : "\"version\":" + version + ",";
        return """
                {
                  %s
                  "collectionId": %d,
                  "creatorUserId": %d,
                  "customerDisplayName": "林女士",
                  "rating": 5,
                  "content": "%s"
                }
                """.formatted(versionField, collectionId, creatorId, content);
    }

    private WorkCollection ensurePublicCollection() {
        ContentCategory category = ensureCategory("Operations Public Category", 800);
        WorkCollection existing = collectionRepository.findAll().stream()
                .filter(item -> "Operations Public Collection".equals(item.getTitle()))
                .findFirst()
                .orElseGet(WorkCollection::new);
        existing.setTitle("Operations Public Collection");
        existing.setDescription("Operations flow test");
        existing.setEventDate(LocalDate.of(2026, 10, 18));
        existing.setRegionCode("330100");
        existing.setLocationText("杭州");
        existing.setCategoryId(category.getId());
        existing.setVisibility(ContentVisibility.PUBLIC);
        existing.setReviewStatus(ReviewStatus.APPROVED);
        existing.setPublishStatus(PublishStatus.PUBLISHED);
        existing.setPublishedAt(Instant.now());
        existing.setPublishedBy(admin.getId());
        existing.setSortOrder(0);
        existing.setFeatured(false);
        existing.setPinned(false);
        if (existing.getCreatedBy() == null) {
            existing.setCreatedBy(creator.getId());
        }
        existing.setUpdatedBy(creator.getId());
        return collectionRepository.saveAndFlush(existing);
    }

    private CarouselFixture ensureCarouselFixture() {
        ContentCategory category = ensureCategory("Operations Carousel Category", 900);

        WorkCollection collection = collectionRepository.findAll().stream()
                .filter(item -> "Operations Carousel Collection".equals(item.getTitle()))
                .findFirst()
                .orElseGet(WorkCollection::new);
        collection.setTitle("Operations Carousel Collection");
        collection.setDescription("Homepage carousel integration test collection");
        collection.setCategoryId(category.getId());
        collection.setEventDate(LocalDate.of(2026, 10, 18));
        collection.setRegionCode("330100");
        collection.setLocationText("杭州");
        collection.setVisibility(ContentVisibility.PUBLIC);
        collection.setReviewStatus(ReviewStatus.APPROVED);
        collection.setPublishStatus(PublishStatus.PUBLISHED);
        collection.setPublishedAt(Instant.now());
        collection.setPublishedBy(admin.getId());
        collection.setSortOrder(0);
        collection.setFeatured(false);
        collection.setPinned(false);
        if (collection.getCreatedBy() == null) {
            collection.setCreatedBy(creator.getId());
        }
        collection.setUpdatedBy(creator.getId());
        collection = collectionRepository.saveAndFlush(collection);

        MediaAsset asset = assetRepository.findAll().stream()
                .filter(item -> "operations-carousel.jpg".equals(item.getStorageName()))
                .findFirst()
                .orElseGet(MediaAsset::new);
        asset.setOriginalName("operations-carousel.jpg");
        asset.setStorageName("operations-carousel.jpg");
        asset.setMimeType("image/jpeg");
        asset.setFileSize(1024L);
        asset.setWidth(1600);
        asset.setHeight(1067);
        asset.setOriginalPath("originals/operations-carousel.jpg");
        asset.setPreviewPath("previews/operations-carousel.jpg");
        asset.setThumbnailPath("thumbnails/operations-carousel.jpg");
        asset.setChecksum("a".repeat(64));
        asset.setProcessStatus("SUCCESS");
        if (asset.getCreatedBy() == null) {
            asset.setCreatedBy(creator.getId());
        }
        asset.setUpdatedBy(creator.getId());
        asset = assetRepository.saveAndFlush(asset);

        Long collectionId = collection.getId();
        Long assetId = asset.getId();
        CollectionPhoto photo = photoRepository.findAll().stream()
                .filter(item -> assetId.equals(item.getAssetId()))
                .findFirst()
                .orElseGet(CollectionPhoto::new);
        photo.setCollectionId(collectionId);
        photo.setAssetId(assetId);
        photo.setSortOrder(0);
        photo.setReviewStatus(ReviewStatus.APPROVED);
        photo.setReviewedAt(Instant.now());
        photo.setReviewedBy(admin.getId());
        if (photo.getCreatedBy() == null) {
            photo.setCreatedBy(creator.getId());
        }
        photo.setUpdatedBy(creator.getId());
        photo = photoRepository.saveAndFlush(photo);

        collection = collectionRepository.findById(collectionId).orElseThrow();
        collection.setCoverPhotoId(photo.getId());
        collection.setUpdatedBy(creator.getId());
        collection = collectionRepository.saveAndFlush(collection);
        return new CarouselFixture(collection, photo);
    }

    private ContentCategory ensureCategory(String name, int sortOrder) {
        return categoryRepository.findAllByDeletedFalseOrderBySortOrderAscCreatedAtAsc().stream()
                .filter(item -> name.equals(item.getName()))
                .findFirst()
                .orElseGet(() -> {
                    ContentCategory created = new ContentCategory();
                    created.setName(name);
                    created.setDescription("Operations integration test");
                    created.setSortOrder(sortOrder);
                    created.setStatus("ACTIVE");
                    created.setCreatedBy(admin.getId());
                    created.setUpdatedBy(admin.getId());
                    return categoryRepository.saveAndFlush(created);
                });
    }

    private SystemUser ensureAccount(String mobile, String roleCode, String displayName) {
        return userRepository.findByMobileAndDeletedFalse(mobile).orElseGet(() -> {
            SystemRole role = roleRepository.findByCodeAndStatus(roleCode, "ACTIVE").orElseThrow();
            SystemUser user = new SystemUser();
            user.setMobile(mobile);
            user.setPasswordHash(passwordEncoder.encode(PASSWORD));
            user.setDisplayName(displayName);
            user.setAccountType(roleCode);
            user.setAccountStatus("ACTIVE");
            user.setMustChangePassword(false);
            user.setProfileCompleted(true);
            user.setDeleted(false);
            user.setRoles(new HashSet<>(Set.of(role)));
            return userRepository.saveAndFlush(user);
        });
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

    private record CarouselFixture(WorkCollection collection, CollectionPhoto photo) {
    }
}
