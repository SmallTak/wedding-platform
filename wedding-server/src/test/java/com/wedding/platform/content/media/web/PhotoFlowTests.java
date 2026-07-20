package com.wedding.platform.content.media.web;

import com.jayway.jsonpath.JsonPath;
import com.wedding.platform.content.collection.persistence.entity.ContentCategory;
import com.wedding.platform.content.collection.persistence.entity.ContentTag;
import com.wedding.platform.content.collection.persistence.repository.ContentCategoryRepository;
import com.wedding.platform.content.collection.persistence.repository.ContentTagRepository;
import com.wedding.platform.content.media.application.CollectionPhotoService;
import com.wedding.platform.content.media.persistence.entity.CollectionPhoto;
import com.wedding.platform.content.media.persistence.entity.MediaAsset;
import com.wedding.platform.content.media.persistence.repository.CollectionPhotoRepository;
import com.wedding.platform.content.media.persistence.repository.MediaAssetRepository;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PhotoFlowTests {

    private static final String PASSWORD = "Photo@Test123";
    private static final String ADMIN_MOBILE = "13800000301";
    private static final String OWNER_MOBILE = "13900000301";
    private static final String OUTSIDER_MOBILE = "13900000302";

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
    private CollectionPhotoRepository photoRepository;

    @Autowired
    private MediaAssetRepository assetRepository;

    @Autowired
    private CollectionPhotoService photoService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private SystemUser admin;
    private SystemUser owner;
    private ContentCategory category;
    private ContentTag tag;

    @BeforeEach
    void createFixtures() {
        admin = ensureAccount(ADMIN_MOBILE, "ADMIN", "Photo Admin");
        owner = ensureAccount(OWNER_MOBILE, "CREATOR", "Photo Owner");
        ensureAccount(OUTSIDER_MOBILE, "CREATOR", "Photo Outsider");
        category = ensureCategory();
        tag = ensureTag();
    }

    @Test
    void ownerCanUploadOrderCoverAndLogicallyDeletePhotos() throws Exception {
        String ownerToken = login(OWNER_MOBILE);
        CollectionReference collection = createCollection(ownerToken, "Photo Processing Collection");

        MockMultipartFile landscape = new MockMultipartFile(
                "files",
                "ceremony.jpg",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                image("jpeg", 1200, 800, new Color(96, 74, 52))
        );
        MockMultipartFile portrait = new MockMultipartFile(
                "files",
                "portrait.png",
                MediaType.TEXT_PLAIN_VALUE,
                image("png", 640, 960, new Color(54, 88, 112))
        );

        String uploadJson = mockMvc.perform(multipart("/api/collections/{collectionId}/photos", collection.id())
                        .file(landscape)
                        .file(portrait)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photos.length()").value(2))
                .andExpect(jsonPath("$.photos[0].mimeType").value("image/jpeg"))
                .andExpect(jsonPath("$.photos[1].mimeType").value("image/png"))
                .andExpect(jsonPath("$.photos[0].previewUrl").value(
                        org.hamcrest.Matchers.startsWith("/media/previews/")))
                .andExpect(jsonPath("$.photos[0].thumbnailUrl").value(
                        org.hamcrest.Matchers.startsWith("/media/thumbnails/")))
                .andReturn().getResponse().getContentAsString();

        Number uploadVersion = JsonPath.read(uploadJson, "$.collectionVersion");
        Number firstPhotoId = JsonPath.read(uploadJson, "$.photos[0].id");
        Number secondPhotoId = JsonPath.read(uploadJson, "$.photos[1].id");
        String firstPreviewUrl = JsonPath.read(uploadJson, "$.photos[0].previewUrl");
        String secondThumbnailUrl = JsonPath.read(uploadJson, "$.photos[1].thumbnailUrl");

        Path firstPreview = storagePath(firstPreviewUrl);
        Path secondThumbnail = storagePath(secondThumbnailUrl);
        org.junit.jupiter.api.Assertions.assertTrue(Files.isRegularFile(firstPreview));
        org.junit.jupiter.api.Assertions.assertTrue(Files.isRegularFile(secondThumbnail));
        BufferedImage thumbnailImage = ImageIO.read(secondThumbnail.toFile());
        org.junit.jupiter.api.Assertions.assertTrue(
                Math.max(thumbnailImage.getWidth(), thumbnailImage.getHeight()) <= 480);

        String coverJson = mockMvc.perform(put("/api/collections/{collectionId}/cover", collection.id())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "photoId": %d
                                }
                                """.formatted(uploadVersion.longValue(), firstPhotoId.longValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverPhotoId").value(firstPhotoId.longValue()))
                .andReturn().getResponse().getContentAsString();
        Number coverVersion = JsonPath.read(coverJson, "$.collectionVersion");

        String orderJson = mockMvc.perform(put("/api/collections/{collectionId}/photos/order", collection.id())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "photoIds": [%d, %d]
                                }
                                """.formatted(
                                coverVersion.longValue(),
                                secondPhotoId.longValue(),
                                firstPhotoId.longValue()
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photos[0].id").value(secondPhotoId.longValue()))
                .andExpect(jsonPath("$.photos[0].sortOrder").value(0))
                .andReturn().getResponse().getContentAsString();
        Number orderVersion = JsonPath.read(orderJson, "$.collectionVersion");

        CollectionPhoto firstPhoto = photoRepository.findById(firstPhotoId.longValue()).orElseThrow();
        MediaAsset firstAsset = assetRepository.findById(firstPhoto.getAssetId()).orElseThrow();
        Path originalFile = Path.of("./build/test-storage").toAbsolutePath().normalize()
                .resolve(firstAsset.getOriginalPath()).normalize();

        mockMvc.perform(delete(
                                "/api/collections/{collectionId}/photos/{photoId}",
                                collection.id(),
                                firstPhotoId.longValue()
                        )
                        .header("Authorization", bearer(ownerToken))
                        .param("version", orderVersion.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverPhotoId").doesNotExist())
                .andExpect(jsonPath("$.photos.length()").value(1))
                .andExpect(jsonPath("$.photos[0].id").value(secondPhotoId.longValue()));

        CollectionPhoto deletedPhoto = photoRepository.findById(firstPhotoId.longValue()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(deletedPhoto.getDeleted());
        org.junit.jupiter.api.Assertions.assertNotNull(deletedPhoto.getDeletedAt());
        org.junit.jupiter.api.Assertions.assertTrue(Files.isRegularFile(originalFile));
        org.junit.jupiter.api.Assertions.assertTrue(Files.isRegularFile(firstPreview));
    }

    @Test
    void actualImageContentAndCollectionAccessAreEnforced() throws Exception {
        String ownerToken = login(OWNER_MOBILE);
        String outsiderToken = login(OUTSIDER_MOBILE);
        CollectionReference collection = createCollection(ownerToken, "Photo Validation Collection");

        MockMultipartFile validImage = new MockMultipartFile(
                "files",
                "valid.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                image("jpeg", 320, 240, Color.GRAY)
        );
        mockMvc.perform(multipart("/api/collections/{collectionId}/photos", collection.id())
                        .file(validImage)
                        .header("Authorization", bearer(outsiderToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COLLECTION_ACCESS_DENIED"));

        MockMultipartFile fakeImage = new MockMultipartFile(
                "files",
                "fake.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "not-an-image".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/collections/{collectionId}/photos", collection.id())
                        .file(fakeImage)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMAGE_FORMAT_INVALID"));

        org.junit.jupiter.api.Assertions.assertTrue(
                photoRepository.findAllByCollectionIdAndDeletedFalseOrderBySortOrderAscIdAsc(collection.id()).isEmpty());
    }

    @Test
    void generatedFilesAreRemovedWhenUploadTransactionRollsBack() throws Exception {
        String ownerToken = login(OWNER_MOBILE);
        CollectionReference collection = createCollection(ownerToken, "Photo Rollback Collection");
        MockMultipartFile image = new MockMultipartFile(
                "files",
                "rollback.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                image("jpeg", 480, 320, Color.DARK_GRAY)
        );
        List<Path> generatedFiles = new ArrayList<>();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            photoService.upload(owner.getId(), collection.id(), List.of(image), "127.0.0.1");
            CollectionPhoto photo = photoRepository
                    .findAllByCollectionIdAndDeletedFalseOrderBySortOrderAscIdAsc(collection.id())
                    .get(0);
            MediaAsset asset = assetRepository.findById(photo.getAssetId()).orElseThrow();
            Path storageRoot = Path.of("./build/test-storage").toAbsolutePath().normalize();
            generatedFiles.add(storageRoot.resolve(asset.getOriginalPath()).normalize());
            generatedFiles.add(storageRoot.resolve(asset.getPreviewPath()).normalize());
            generatedFiles.add(storageRoot.resolve(asset.getThumbnailPath()).normalize());
            status.setRollbackOnly();
        });

        org.junit.jupiter.api.Assertions.assertTrue(
                photoRepository.findAllByCollectionIdAndDeletedFalseOrderBySortOrderAscIdAsc(collection.id()).isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(
                generatedFiles.stream().noneMatch(Files::exists));
    }

    private CollectionReference createCollection(String ownerToken, String title) throws Exception {
        String response = mockMvc.perform(post("/api/collections")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "description": "Collection photo integration test",
                                  "categoryId": %d,
                                  "tagIds": [%d]
                                }
                                """.formatted(title, category.getId(), tag.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(response, "$.id");
        Number version = JsonPath.read(response, "$.version");
        return new CollectionReference(id.longValue(), version.longValue());
    }

    private byte[] image(String format, int width, int height, Color color) throws Exception {
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
        org.junit.jupiter.api.Assertions.assertTrue(ImageIO.write(image, format, output));
        return output.toByteArray();
    }

    private Path storagePath(String publicUrl) {
        String relative = publicUrl.substring("/media/".length());
        return Path.of("./build/test-storage").toAbsolutePath().normalize().resolve(relative).normalize();
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
        String name = "Photo Test Category";
        ContentCategory value = categoryRepository.findAllByDeletedFalseOrderBySortOrderAscCreatedAtAsc().stream()
                .filter(item -> name.equals(item.getName()))
                .findFirst()
                .orElseGet(() -> {
                    ContentCategory created = new ContentCategory();
                    created.setName(name);
                    created.setCreatedBy(admin.getId());
                    return created;
                });
        value.setDescription("Photo integration test category");
        value.setSortOrder(100);
        value.setStatus("ACTIVE");
        value.setUpdatedBy(admin.getId());
        return categoryRepository.saveAndFlush(value);
    }

    private ContentTag ensureTag() {
        String name = "Photo Test Tag";
        ContentTag value = tagRepository.findAllByDeletedFalseOrderBySortOrderAscCreatedAtAsc().stream()
                .filter(item -> name.equals(item.getName()))
                .findFirst()
                .orElseGet(() -> {
                    ContentTag created = new ContentTag();
                    created.setName(name);
                    created.setCreatedBy(admin.getId());
                    return created;
                });
        value.setSortOrder(100);
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

    private record CollectionReference(long id, long version) {
    }
}
