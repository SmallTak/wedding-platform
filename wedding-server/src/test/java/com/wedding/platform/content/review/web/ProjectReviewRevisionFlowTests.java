package com.wedding.platform.content.review.web;

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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasSize;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectReviewRevisionFlowTests {

    private static final String PASSWORD = "ProjectReview@Test123";
    private static final String ADMIN_MOBILE = "13800000501";
    private static final String OWNER_MOBILE = "13900000501";
    private static final String OUTSIDER_MOBILE = "13900000502";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SystemUserRepository userRepository;

    @Autowired
    private SystemRoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void createAccounts() {
        ensureAccount(ADMIN_MOBILE, "ADMIN", "Project Review Admin");
        ensureAccount(OWNER_MOBILE, "CREATOR", "Project Review Owner");
        ensureAccount(OUTSIDER_MOBILE, "CREATOR", "Project Review Outsider");
    }

    @Test
    void onlyChangedRejectedProjectFieldCreatesANewRevisionItem() throws Exception {
        String adminToken = login(ADMIN_MOBILE);
        String ownerToken = login(OWNER_MOBILE);
        String outsiderToken = login(OUTSIDER_MOBILE);

        String createdJson = mockMvc.perform(post("/api/projects")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Original Project Title",
                                  "coupleDisplayName": "M & Q",
                                  "eventDate": "2026-12-12",
                                  "regionCode": "330100",
                                  "locationText": "Hangzhou",
                                  "description": "Initial project description"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number projectId = JsonPath.read(createdJson, "$.id");
        Number projectVersion = JsonPath.read(createdJson, "$.version");

        mockMvc.perform(post("/api/projects/{projectId}/submit", projectId.longValue())
                        .header("Authorization", bearer(outsiderToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + projectVersion + "}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT_ACCESS_DENIED"));

        String submittedJson = mockMvc.perform(post("/api/projects/{projectId}/submit", projectId.longValue())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + projectVersion + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.project.reviewStatus").value("PENDING"))
                .andExpect(jsonPath("$.reviewHistory.revisions[0].revisionNo").value(1))
                .andExpect(jsonPath("$.reviewHistory.revisions[0].items.length()").value(6))
                .andReturn().getResponse().getContentAsString();
        Number submittedVersion = JsonPath.read(submittedJson, "$.project.version");
        List<Number> approvedFieldIds = JsonPath.read(
                submittedJson,
                "$.reviewHistory.currentItems[?(@.fieldKey != 'TITLE' && @.fieldKey != 'DESCRIPTION')].id"
        );
        List<Number> rejectedFieldIds = JsonPath.read(
                submittedJson,
                "$.reviewHistory.currentItems[?(@.fieldKey == 'TITLE' || @.fieldKey == 'DESCRIPTION')].id"
        );

        String approvedJson = reviewFields(
                adminToken,
                projectId,
                submittedVersion,
                approvedFieldIds,
                "APPROVE",
                null
        );
        Number approvedVersion = JsonPath.read(approvedJson, "$.project.version");

        String rejectedJson = reviewFields(
                adminToken,
                projectId,
                approvedVersion,
                rejectedFieldIds,
                "REJECT",
                "Revise the project title and description"
        );
        Number rejectedVersion = JsonPath.read(rejectedJson, "$.project.version");
        org.junit.jupiter.api.Assertions.assertEquals(
                "PARTIALLY_REJECTED",
                JsonPath.read(rejectedJson, "$.project.reviewStatus")
        );

        String updatedJson = mockMvc.perform(put("/api/projects/{projectId}", projectId.longValue())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "title": "Revised Project Title",
                                  "coupleDisplayName": "M & Q",
                                  "eventDate": "2026-12-12",
                                  "regionCode": "330100",
                                  "locationText": "Hangzhou",
                                  "description": "Initial project description"
                                }
                                """.formatted(rejectedVersion.longValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        Number updatedVersion = JsonPath.read(updatedJson, "$.version");

        String resubmittedJson = mockMvc.perform(post("/api/projects/{projectId}/submit", projectId.longValue())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + updatedVersion + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewHistory.revisions.length()").value(2))
                .andExpect(jsonPath("$.reviewHistory.revisions[0].revisionNo").value(2))
                .andExpect(jsonPath("$.reviewHistory.revisions[0].items.length()").value(1))
                .andExpect(jsonPath("$.reviewHistory.revisions[0].items[0].fieldKey").value("TITLE"))
                .andExpect(jsonPath("$.project.reviewStatus").value("PARTIALLY_REJECTED"))
                .andExpect(jsonPath(
                        "$.reviewHistory.currentItems[?(@.status == 'APPROVED')]",
                        hasSize(4)
                ))
                .andReturn().getResponse().getContentAsString();
        Number resubmittedVersion = JsonPath.read(resubmittedJson, "$.project.version");
        List<Number> revisedTitleIds = JsonPath.read(
                resubmittedJson,
                "$.reviewHistory.currentItems[?(@.fieldKey == 'TITLE')].id"
        );

        String approvedTitleJson = reviewFields(
                adminToken,
                projectId,
                resubmittedVersion,
                revisedTitleIds,
                "APPROVE",
                null
        );
        Number approvedTitleVersion = JsonPath.read(approvedTitleJson, "$.project.version");
        org.junit.jupiter.api.Assertions.assertEquals(
                "PARTIALLY_REJECTED",
                JsonPath.read(approvedTitleJson, "$.project.reviewStatus")
        );

        String updatedDescriptionJson = mockMvc.perform(put(
                                "/api/projects/{projectId}", projectId.longValue())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "title": "Revised Project Title",
                                  "coupleDisplayName": "M & Q",
                                  "eventDate": "2026-12-12",
                                  "regionCode": "330100",
                                  "locationText": "Hangzhou",
                                  "description": "Revised project description"
                                }
                                """.formatted(approvedTitleVersion.longValue())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Number updatedDescriptionVersion = JsonPath.read(updatedDescriptionJson, "$.version");

        String thirdRevisionJson = mockMvc.perform(post(
                                "/api/projects/{projectId}/submit", projectId.longValue())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + updatedDescriptionVersion + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.project.reviewStatus").value("PENDING"))
                .andExpect(jsonPath("$.reviewHistory.revisions.length()").value(3))
                .andExpect(jsonPath("$.reviewHistory.revisions[0].items.length()").value(1))
                .andExpect(jsonPath("$.reviewHistory.revisions[0].items[0].fieldKey").value("DESCRIPTION"))
                .andReturn().getResponse().getContentAsString();
        Number thirdRevisionVersion = JsonPath.read(thirdRevisionJson, "$.project.version");
        List<Number> revisedDescriptionIds = JsonPath.read(
                thirdRevisionJson,
                "$.reviewHistory.currentItems[?(@.fieldKey == 'DESCRIPTION')].id"
        );

        String finalApprovedJson = reviewFields(
                adminToken,
                projectId,
                thirdRevisionVersion,
                revisedDescriptionIds,
                "APPROVE",
                null
        );
        Number readyVersion = JsonPath.read(finalApprovedJson, "$.project.version");

        mockMvc.perform(get("/api/projects/{projectId}/review", projectId.longValue())
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.project.reviewStatus").value("APPROVED"))
                .andExpect(jsonPath("$.project.publishStatus").value("READY"))
                .andExpect(jsonPath(
                        "$.reviewHistory.currentItems[?(@.status == 'APPROVED')]",
                        hasSize(6)
                ));

        mockMvc.perform(get("/api/admin/reviews/projects")
                        .header("Authorization", bearer(adminToken))
                        .param("keyword", "Revised Project Title"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].approvedFields").value(6));

        mockMvc.perform(post("/api/admin/reviews/projects/{projectId}/publish", projectId.longValue())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "visibility": "PASSWORD"
                                }
                                """.formatted(readyVersion.longValue())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_VISIBILITY_NOT_SUPPORTED"));

        mockMvc.perform(post("/api/admin/reviews/projects/{projectId}/publish", projectId.longValue())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "visibility": "PUBLIC"
                                }
                                """.formatted(readyVersion.longValue())))
                .andExpect(status().isForbidden());

        String publishedJson = mockMvc.perform(post(
                                "/api/admin/reviews/projects/{projectId}/publish",
                                projectId.longValue())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "visibility": "PUBLIC"
                                }
                                """.formatted(readyVersion.longValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.project.publishStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.project.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.project.publishedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        Number publishedVersion = JsonPath.read(publishedJson, "$.project.version");

        mockMvc.perform(put("/api/projects/{projectId}", projectId.longValue())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "title": "Published Project Edit",
                                  "coupleDisplayName": "M & Q",
                                  "eventDate": "2026-12-12",
                                  "regionCode": "330100",
                                  "locationText": "Hangzhou",
                                  "description": "Published projects remain locked"
                                }
                                """.formatted(publishedVersion.longValue())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROJECT_PUBLISHED_LOCKED"));

        String offlineJson = mockMvc.perform(post(
                                "/api/admin/reviews/projects/{projectId}/offline",
                                projectId.longValue())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "reason": "Project information needs revision"
                                }
                                """.formatted(publishedVersion.longValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.project.publishStatus").value("OFFLINE"))
                .andExpect(jsonPath("$.project.offlineReason")
                        .value("Project information needs revision"))
                .andReturn().getResponse().getContentAsString();
        Number offlineVersion = JsonPath.read(offlineJson, "$.project.version");

        mockMvc.perform(put("/api/projects/{projectId}", projectId.longValue())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "title": "Offline Editable Project",
                                  "coupleDisplayName": "M & Q",
                                  "eventDate": "2026-12-12",
                                  "regionCode": "330100",
                                  "locationText": "Hangzhou",
                                  "description": "Editing is available after offline"
                                }
                                """.formatted(offlineVersion.longValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("DRAFT"))
                .andExpect(jsonPath("$.publishStatus").value("OFFLINE"));
    }

    private String reviewFields(
            String adminToken,
            Number projectId,
            Number version,
            List<Number> fieldIds,
            String decision,
            String reason
    ) throws Exception {
        String reasonField = reason == null ? "" : ",\"reason\":\"" + reason + "\"";
        return mockMvc.perform(put("/api/admin/reviews/projects/{projectId}/fields", projectId.longValue())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "reviewItemIds": [%s],
                                  "decision": "%s"
                                  %s
                                }
                                """.formatted(
                                version.longValue(),
                                fieldIds.stream().map(String::valueOf).collect(Collectors.joining(",")),
                                decision,
                                reasonField
                        )))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
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
