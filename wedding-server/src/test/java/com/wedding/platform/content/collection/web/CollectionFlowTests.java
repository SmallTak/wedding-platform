package com.wedding.platform.content.collection.web;

import com.jayway.jsonpath.JsonPath;
import com.wedding.platform.content.collection.persistence.entity.ContentCategory;
import com.wedding.platform.content.collection.persistence.entity.ContentTag;
import com.wedding.platform.content.collection.persistence.repository.ContentCategoryRepository;
import com.wedding.platform.content.collection.persistence.repository.ContentTagRepository;
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

import java.util.HashSet;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasSize;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CollectionFlowTests {

    private static final String PASSWORD = "Collection@Test123";
    private static final String ADMIN_MOBILE = "13800000201";
    private static final String OWNER_MOBILE = "13900000201";
    private static final String COLLABORATOR_MOBILE = "13900000202";
    private static final String OUTSIDER_MOBILE = "13900000203";

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
    private PasswordEncoder passwordEncoder;

    private SystemUser admin;
    private SystemUser owner;
    private SystemUser collaborator;
    private ContentCategory category;
    private ContentTag primaryTag;
    private ContentTag secondaryTag;

    @BeforeEach
    void createFixtures() {
        admin = ensureAccount(ADMIN_MOBILE, "ADMIN", "Collection Admin");
        owner = ensureAccount(OWNER_MOBILE, "CREATOR", "Collection Owner");
        collaborator = ensureAccount(COLLABORATOR_MOBILE, "CREATOR", "Collection Collaborator");
        ensureAccount(OUTSIDER_MOBILE, "CREATOR", "Collection Outsider");
        category = ensureCategory("Collection Test Category", 10);
        primaryTag = ensureTag("Collection Test Primary", 10);
        secondaryTag = ensureTag("Collection Test Secondary", 20);
    }

    @Test
    void adminCanManageContentConfigurationAndCreatorCannot() throws Exception {
        String adminToken = login(ADMIN_MOBILE);
        String ownerToken = login(OWNER_MOBILE);
        String uniqueName = "Editorial-" + System.nanoTime();

        String categoryJson = mockMvc.perform(post("/api/admin/content/categories")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "description": "Editorial wedding stories",
                                  "sortOrder": 30
                                }
                                """.formatted(uniqueName)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        Number categoryId = JsonPath.read(categoryJson, "$.id");
        Number categoryVersion = JsonPath.read(categoryJson, "$.version");

        mockMvc.perform(post("/api/admin/content/categories")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "sortOrder": 40
                                }
                                """.formatted(uniqueName.toLowerCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_NAME_EXISTS"));

        String updatedCategoryJson = mockMvc.perform(put(
                                "/api/admin/content/categories/{categoryId}", categoryId.longValue())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "name": "%s",
                                  "description": "Disabled editorial category",
                                  "sortOrder": 30,
                                  "status": "DISABLED"
                                }
                                """.formatted(categoryVersion.longValue(), uniqueName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"))
                .andReturn().getResponse().getContentAsString();
        Number updatedCategoryVersion = JsonPath.read(updatedCategoryJson, "$.version");

        String tagJson = mockMvc.perform(post("/api/admin/content/tags")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Tag-%d",
                                  "sortOrder": 30
                                }
                                """.formatted(System.nanoTime())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        Number tagId = JsonPath.read(tagJson, "$.id");
        Number tagVersion = JsonPath.read(tagJson, "$.version");

        String updatedTagJson = mockMvc.perform(put(
                                "/api/admin/content/tags/{tagId}", tagId.longValue())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "name": "Updated-Tag-%d",
                                  "sortOrder": 35,
                                  "status": "DISABLED"
                                }
                                """.formatted(tagVersion.longValue(), tagId.longValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"))
                .andReturn().getResponse().getContentAsString();
        Number updatedTagVersion = JsonPath.read(updatedTagJson, "$.version");

        mockMvc.perform(get("/api/admin/content/categories")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(delete("/api/admin/content/categories/{categoryId}", categoryId.longValue())
                        .header("Authorization", bearer(adminToken))
                        .param("version", updatedCategoryVersion.toString()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/admin/content/tags/{tagId}", tagId.longValue())
                        .header("Authorization", bearer(adminToken))
                        .param("version", updatedTagVersion.toString()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/content/categories")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)]".formatted(categoryId.longValue()), hasSize(0)));
        mockMvc.perform(get("/api/admin/content/tags")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)]".formatted(tagId.longValue()), hasSize(0)));
    }

    @Test
    void independentCollectionSupportsCollaborationAndOptimisticLocking() throws Exception {
        String adminToken = login(ADMIN_MOBILE);
        String ownerToken = login(OWNER_MOBILE);
        String collaboratorToken = login(COLLABORATOR_MOBILE);
        String outsiderToken = login(OUTSIDER_MOBILE);

        mockMvc.perform(get("/api/collections/options")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[?(@.id == %d)]".formatted(category.getId())).exists())
                .andExpect(jsonPath("$.tags[?(@.id == %d)]".formatted(primaryTag.getId())).exists());

        String createdJson = mockMvc.perform(post("/api/collections")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCollectionRequest(
                                null,
                                "Independent Wedding Story",
                                category.getId(),
                                primaryTag.getId()
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.project").doesNotExist())
                .andExpect(jsonPath("$.visibility").value("HIDDEN"))
                .andExpect(jsonPath("$.reviewStatus").value("DRAFT"))
                .andExpect(jsonPath("$.publishStatus").value("UNPUBLISHED"))
                .andExpect(jsonPath("$.creators.length()").value(1))
                .andExpect(jsonPath("$.creators[0].userId").value(owner.getId()))
                .andReturn().getResponse().getContentAsString();
        Number collectionId = JsonPath.read(createdJson, "$.id");
        Number createdVersion = JsonPath.read(createdJson, "$.version");

        mockMvc.perform(delete("/api/admin/content/categories/{categoryId}", category.getId())
                        .header("Authorization", bearer(adminToken))
                        .param("version", category.getVersion().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"));
        mockMvc.perform(delete("/api/admin/content/tags/{tagId}", primaryTag.getId())
                        .header("Authorization", bearer(adminToken))
                        .param("version", primaryTag.getVersion().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TAG_IN_USE"));

        mockMvc.perform(get("/api/collections/{collectionId}", collectionId.longValue())
                        .header("Authorization", bearer(outsiderToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COLLECTION_ACCESS_DENIED"));

        String assignedJson = mockMvc.perform(put(
                                "/api/admin/collections/{collectionId}/creators", collectionId.longValue())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "creatorUserIds": [%d]
                                }
                                """.formatted(createdVersion.longValue(), collaborator.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creators.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        Number assignedVersion = JsonPath.read(assignedJson, "$.version");

        String updatedJson = mockMvc.perform(put("/api/collections/{collectionId}", collectionId.longValue())
                        .header("Authorization", bearer(collaboratorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateCollectionRequest(
                                assignedVersion.longValue(),
                                null,
                                "Collaborative Wedding Story",
                                category.getId(),
                                primaryTag.getId(),
                                secondaryTag.getId()
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Collaborative Wedding Story"))
                .andExpect(jsonPath("$.tags.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        Number updatedVersion = JsonPath.read(updatedJson, "$.version");

        mockMvc.perform(put("/api/collections/{collectionId}", collectionId.longValue())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateCollectionRequest(
                                assignedVersion.longValue(),
                                null,
                                "Stale Collection Update",
                                category.getId(),
                                primaryTag.getId()
                        )))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COLLECTION_VERSION_CONFLICT"));

        mockMvc.perform(get("/api/collections")
                        .header("Authorization", bearer(adminToken))
                        .param("keyword", "Collaborative Wedding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(collectionId.longValue()))
                .andExpect(jsonPath("$.content[0].version").value(updatedVersion.longValue()));

        mockMvc.perform(delete("/api/collections/{collectionId}", collectionId.longValue())
                        .header("Authorization", bearer(outsiderToken))
                        .param("version", updatedVersion.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COLLECTION_ACCESS_DENIED"));

        mockMvc.perform(delete("/api/collections/{collectionId}", collectionId.longValue())
                        .header("Authorization", bearer(collaboratorToken))
                        .param("version", updatedVersion.toString()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/collections/{collectionId}", collectionId.longValue())
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/collections")
                        .header("Authorization", bearer(adminToken))
                        .param("keyword", "Collaborative Wedding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
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

    private ContentCategory ensureCategory(String name, int sortOrder) {
        ContentCategory value = categoryRepository.findAllByDeletedFalseOrderBySortOrderAscCreatedAtAsc().stream()
                .filter(item -> name.equals(item.getName()))
                .findFirst()
                .orElseGet(() -> {
                    ContentCategory created = new ContentCategory();
                    created.setName(name);
                    created.setCreatedBy(admin.getId());
                    return created;
                });
        value.setDescription("Collection integration test category");
        value.setSortOrder(sortOrder);
        value.setStatus("ACTIVE");
        value.setUpdatedBy(admin.getId());
        return categoryRepository.saveAndFlush(value);
    }

    private ContentTag ensureTag(String name, int sortOrder) {
        ContentTag value = tagRepository.findAllByDeletedFalseOrderBySortOrderAscCreatedAtAsc().stream()
                .filter(item -> name.equals(item.getName()))
                .findFirst()
                .orElseGet(() -> {
                    ContentTag created = new ContentTag();
                    created.setName(name);
                    created.setCreatedBy(admin.getId());
                    return created;
                });
        value.setSortOrder(sortOrder);
        value.setStatus("ACTIVE");
        value.setUpdatedBy(admin.getId());
        return tagRepository.saveAndFlush(value);
    }

    private String createCollectionRequest(Long projectId, String title, Long categoryId, Long tagId) {
        return """
                {
                  "title": "%s",
                  "description": "A complete wedding story",
                  "categoryId": %d,
                  "tagIds": [%d]
                }
                """.formatted(title, categoryId, tagId);
    }

    private String updateCollectionRequest(
            long version,
            Long projectId,
            String title,
            Long categoryId,
            Long... tagIds
    ) {
        String tags = java.util.Arrays.stream(tagIds)
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
        return """
                {
                  "version": %d,
                  "title": "%s",
                  "description": "Updated collaborative wedding story",
                  "categoryId": %d,
                  "tagIds": [%s]
                }
                """.formatted(version, title, categoryId, tags);
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
}
