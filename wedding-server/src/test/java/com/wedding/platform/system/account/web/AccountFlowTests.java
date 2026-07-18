package com.wedding.platform.system.account.web;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountFlowTests {

    private static final String ADMIN_MOBILE = "13800000009";
    private static final String ADMIN_PASSWORD = "Admin@Test123";
    private static final String CREATOR_MOBILE = "13900000009";
    private static final String CREATOR_PASSWORD = "Creator@Test123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SystemUserRepository userRepository;

    @Autowired
    private SystemRoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void createAdmin() {
        if (userRepository.findByMobileAndDeletedFalse(ADMIN_MOBILE).isPresent()) {
            return;
        }
        SystemRole role = roleRepository.findByCodeAndStatus("ADMIN", "ACTIVE").orElseThrow();
        SystemUser admin = new SystemUser();
        admin.setMobile(ADMIN_MOBILE);
        admin.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
        admin.setDisplayName("Test Admin");
        admin.setAccountType("ADMIN");
        admin.setAccountStatus("ACTIVE");
        admin.setMustChangePassword(false);
        admin.setProfileCompleted(true);
        admin.setDeleted(false);
        admin.setRoles(new HashSet<>(Set.of(role)));
        userRepository.saveAndFlush(admin);
    }

    @Test
    void adminCanOpenCreatorAndCreatorCanCompleteFirstLogin() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        String adminToken = login(ADMIN_MOBILE, ADMIN_PASSWORD, false);
        String rolesJson = mockMvc.perform(get("/api/admin/professional-roles")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Number professionalRoleId = JsonPath.read(rolesJson, "$[0].id");

        String creatorRequest = """
                {
                  "mobile": "%s",
                  "initialPassword": "%s",
                  "displayName": "Test Creator",
                  "professionalRoleIds": [%d]
                }
                """.formatted(CREATOR_MOBILE, CREATOR_PASSWORD, professionalRoleId.longValue());
        String creatorJson = mockMvc.perform(post("/api/admin/creators")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(creatorRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountType").value("CREATOR"))
                .andExpect(jsonPath("$.setupRequired").value(true))
                .andExpect(jsonPath("$.version").isNumber())
                .andReturn().getResponse().getContentAsString();
        Number creatorId = JsonPath.read(creatorJson, "$.id");

        String creatorToken = login(CREATOR_MOBILE, CREATOR_PASSWORD, true);
        mockMvc.perform(get("/api/admin/creators")
                        .header("Authorization", bearer(creatorToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_SETUP_REQUIRED"));

        MockMultipartFile avatar = new MockMultipartFile(
                "file",
                "avatar.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xd9}
        );
        String avatarJson = mockMvc.perform(multipart("/api/account/avatar")
                        .file(avatar)
                        .header("Authorization", bearer(creatorToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String avatarPath = JsonPath.read(avatarJson, "$.path");

        mockMvc.perform(put("/api/account/password")
                        .header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"Creator@Test123","newPassword":"Creator@Test456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false));

        String profileRequest = """
                {
                  "displayName": "Test Creator",
                  "avatarPath": "%s",
                  "positionText": "Wedding Photographer",
                  "serviceArea": "Hangzhou",
                  "introduction": "Documentary wedding photography"
                }
                """.formatted(avatarPath);
        String profileJson = mockMvc.perform(put("/api/account/profile")
                        .header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileCompleted").value(true))
                .andExpect(jsonPath("$.setupRequired").value(false))
                .andReturn().getResponse().getContentAsString();
        Number creatorVersion = JsonPath.read(profileJson, "$.version");

        mockMvc.perform(get("/api/admin/creators")
                        .header("Authorization", bearer(creatorToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/admin/creators/{creatorId}", creatorId.longValue())
                        .header("Authorization", bearer(adminToken))
                        .param("version", creatorVersion.toString()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/creators")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)]".formatted(creatorId.longValue())).isEmpty());

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", bearer(creatorToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mobile":"13900000009","password":"Creator@Test456"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void loginAcceptsProductionOrigin() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header("Origin", "https://photo.shop-hz.top")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobile\":\"" + ADMIN_MOBILE + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.accountType").value("ADMIN"));
    }

    private String login(String mobile, String password, boolean setupRequired) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobile\":\"" + mobile + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.setupRequired").value(setupRequired))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
