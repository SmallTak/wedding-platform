package com.wedding.platform.content.project.web;

import com.jayway.jsonpath.JsonPath;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectFlowTests {

    private static final String PASSWORD = "Project@Test123";
    private static final String ADMIN_MOBILE = "13800000101";
    private static final String OWNER_MOBILE = "13900000101";
    private static final String COLLABORATOR_MOBILE = "13900000102";
    private static final String OUTSIDER_MOBILE = "13900000103";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SystemUserRepository userRepository;

    @Autowired
    private SystemRoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private SystemUser admin;
    private SystemUser owner;
    private SystemUser collaborator;
    private SystemUser outsider;

    @BeforeEach
    void createAccounts() {
        admin = ensureAccount(ADMIN_MOBILE, "ADMIN", "Project Admin");
        owner = ensureAccount(OWNER_MOBILE, "CREATOR", "Project Owner");
        collaborator = ensureAccount(COLLABORATOR_MOBILE, "CREATOR", "Project Collaborator");
        outsider = ensureAccount(OUTSIDER_MOBILE, "CREATOR", "Project Outsider");
    }

    @Test
    void projectAccessAssignmentAndOptimisticLockAreEnforced() throws Exception {
        String adminToken = login(ADMIN_MOBILE);
        String ownerToken = login(OWNER_MOBILE);
        String collaboratorToken = login(COLLABORATOR_MOBILE);
        String outsiderToken = login(OUTSIDER_MOBILE);

        String createdJson = mockMvc.perform(post("/api/projects")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "West Lake Summer Wedding",
                                  "coupleDisplayName": "L & Z",
                                  "eventDate": "2026-08-08",
                                  "regionCode": "330100",
                                  "locationText": "Hangzhou, Zhejiang",
                                  "description": "Outdoor ceremony and dinner"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.visibility").value("HIDDEN"))
                .andExpect(jsonPath("$.reviewStatus").value("DRAFT"))
                .andExpect(jsonPath("$.publishStatus").value("UNPUBLISHED"))
                .andExpect(jsonPath("$.creators.length()").value(1))
                .andExpect(jsonPath("$.creators[0].userId").value(owner.getId()))
                .andReturn().getResponse().getContentAsString();
        Number projectId = JsonPath.read(createdJson, "$.id");
        Number createdVersion = JsonPath.read(createdJson, "$.version");

        mockMvc.perform(get("/api/projects/{projectId}", projectId.longValue())
                        .header("Authorization", bearer(outsiderToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT_ACCESS_DENIED"));

        String assignedJson = mockMvc.perform(put("/api/admin/projects/{projectId}/creators", projectId.longValue())
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

        mockMvc.perform(get("/api/projects/{projectId}", projectId.longValue())
                        .header("Authorization", bearer(collaboratorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("West Lake Summer Wedding"));

        String updatedJson = mockMvc.perform(put("/api/projects/{projectId}", projectId.longValue())
                        .header("Authorization", bearer(collaboratorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest(assignedVersion.longValue(), "West Lake Evening Wedding")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("West Lake Evening Wedding"))
                .andReturn().getResponse().getContentAsString();
        Number updatedVersion = JsonPath.read(updatedJson, "$.version");

        mockMvc.perform(put("/api/projects/{projectId}", projectId.longValue())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest(assignedVersion.longValue(), "Stale Wedding Title")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROJECT_VERSION_CONFLICT"));

        mockMvc.perform(get("/api/projects")
                        .header("Authorization", bearer(adminToken))
                        .param("keyword", "Evening Wedding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(projectId.longValue()))
                .andExpect(jsonPath("$.content[0].version").value(updatedVersion.longValue()));
    }

    @Test
    void creatorCannotAssignProjectParticipants() throws Exception {
        String ownerToken = login(OWNER_MOBILE);
        String createdJson = mockMvc.perform(post("/api/projects")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Assignment Permission Test",
                                  "eventDate": "2026-09-09",
                                  "regionCode": "330100",
                                  "locationText": "Hangzhou"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number projectId = JsonPath.read(createdJson, "$.id");
        Number version = JsonPath.read(createdJson, "$.version");

        mockMvc.perform(put("/api/admin/projects/{projectId}/creators", projectId.longValue())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "creatorUserIds": [%d]
                                }
                                """.formatted(version.longValue(), collaborator.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void disabledProjectOwnerIsRetainedWithoutBlockingOtherAssignments() throws Exception {
        String adminToken = login(ADMIN_MOBILE);
        String ownerToken = login(OWNER_MOBILE);
        String createdJson = mockMvc.perform(post("/api/projects")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Disabled Owner Assignment Test",
                                  "eventDate": "2026-10-10",
                                  "regionCode": "330100",
                                  "locationText": "Hangzhou"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number projectId = JsonPath.read(createdJson, "$.id");
        Number version = JsonPath.read(createdJson, "$.version");

        SystemUser currentOwner = userRepository.findById(owner.getId()).orElseThrow();
        currentOwner.setAccountStatus("DISABLED");
        userRepository.saveAndFlush(currentOwner);

        mockMvc.perform(put("/api/admin/projects/{projectId}/creators", projectId.longValue())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "creatorUserIds": [%d]
                                }
                                """.formatted(version.longValue(), collaborator.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creators.length()").value(2))
                .andExpect(jsonPath("$.creators[0].userId").value(owner.getId()))
                .andExpect(jsonPath("$.creators[0].accountStatus").value("DISABLED"));
    }

    @Test
    void projectParticipantCanLogicallyDeleteAnUnpublishedProject() throws Exception {
        String ownerToken = login(OWNER_MOBILE);
        String outsiderToken = login(OUTSIDER_MOBILE);
        String createdJson = mockMvc.perform(post("/api/projects")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Project Pending Deletion",
                                  "eventDate": "2026-12-01",
                                  "regionCode": "330100",
                                  "locationText": "Hangzhou"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number projectId = JsonPath.read(createdJson, "$.id");
        Number version = JsonPath.read(createdJson, "$.version");

        mockMvc.perform(delete("/api/projects/{projectId}", projectId.longValue())
                        .header("Authorization", bearer(outsiderToken))
                        .param("version", version.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT_ACCESS_DENIED"));

        mockMvc.perform(delete("/api/projects/{projectId}", projectId.longValue())
                        .header("Authorization", bearer(ownerToken))
                        .param("version", String.valueOf(version.longValue() + 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROJECT_VERSION_CONFLICT"));

        mockMvc.perform(delete("/api/projects/{projectId}", projectId.longValue())
                        .header("Authorization", bearer(ownerToken))
                        .param("version", version.toString()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/projects/{projectId}", projectId.longValue())
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/projects")
                        .header("Authorization", bearer(ownerToken))
                        .param("keyword", "Project Pending Deletion"))
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

    private String updateRequest(long version, String title) {
        return """
                {
                  "version": %d,
                  "title": "%s",
                  "coupleDisplayName": "L & Z",
                  "eventDate": "2026-08-08",
                  "regionCode": "330100",
                  "locationText": "Hangzhou, Zhejiang",
                  "description": "Updated shared project details"
                }
                """.formatted(version, title);
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
