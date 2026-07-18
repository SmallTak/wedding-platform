package com.wedding.platform.operations.web;

import com.jayway.jsonpath.JsonPath;
import com.wedding.platform.operations.notification.application.UserNotificationService;
import com.wedding.platform.operations.notification.persistence.entity.UserNotificationRelatedType;
import com.wedding.platform.operations.notification.persistence.entity.UserNotificationType;
import com.wedding.platform.operations.notification.persistence.repository.UserNotificationRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserNotificationFlowTests {

    private static final String PASSWORD = "Notifications@Test123";
    private static final String ADMIN_MOBILE = "13800000901";
    private static final String CREATOR_MOBILE = "13900000901";
    private static final String CUSTOMER_MOBILE = "13700000901";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserNotificationService notificationService;

    @Autowired
    private UserNotificationRepository notificationRepository;

    @Autowired
    private SystemUserRepository userRepository;

    @Autowired
    private SystemRoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private SystemUser admin;
    private SystemUser creator;
    private SystemUser customer;

    @BeforeEach
    void createFixtures() {
        admin = ensureAccount(ADMIN_MOBILE, "ADMIN", "Notification Admin");
        creator = ensureAccount(CREATOR_MOBILE, "CREATOR", "Notification Creator");
        customer = ensureAccount(CUSTOMER_MOBILE, "CUSTOMER", "Notification Customer");
        notificationRepository.deleteAll();
    }

    @Test
    void notificationsAreIsolatedByRecipientAndSupportReadWorkflows() throws Exception {
        notificationService.notifyAdmins(
                customer.getId(),
                UserNotificationType.CONSULTATION_NEW,
                "新的咨询线索",
                "收到新的官网咨询线索，请及时跟进。",
                UserNotificationRelatedType.INQUIRY,
                901L
        );
        notificationService.notifyUser(
                creator.getId(),
                admin.getId(),
                UserNotificationType.PROJECT_PARTICIPANT_ADDED,
                "已加入婚礼项目",
                "您已加入一个婚礼项目。",
                UserNotificationRelatedType.PROJECT,
                902L
        );
        notificationService.notifyProjectLinkApproved(
                customer.getId(),
                admin.getId(),
                903L,
                "TS20260718"
        );

        String adminToken = login(ADMIN_MOBILE);
        String creatorToken = login(CREATOR_MOBILE);
        String customerToken = login(CUSTOMER_MOBILE);

        String adminPage = mockMvc.perform(get("/api/notifications")
                        .header("Authorization", bearer(adminToken))
                        .param("unreadOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].type").value("CONSULTATION_NEW"))
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andReturn().getResponse().getContentAsString();
        Number adminNotificationId = JsonPath.read(adminPage, "$.content[0].id");
        Number adminNotificationVersion = JsonPath.read(adminPage, "$.content[0].version");

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", bearer(creatorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].type").value("PROJECT_PARTICIPANT_ADDED"))
                .andExpect(jsonPath("$.unreadCount").value(1));

        mockMvc.perform(get("/api/customer/notifications")
                        .header("Authorization", bearer(customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].type").value("PROJECT_LINK_APPROVED"))
                .andExpect(jsonPath("$.unreadCount").value(1));

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", bearer(customerToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/customer/notifications")
                        .header("Authorization", bearer(creatorToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/notifications/{notificationId}/read", adminNotificationId.longValue())
                        .header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + adminNotificationVersion.longValue() + "}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOTIFICATION_NOT_FOUND"));

        mockMvc.perform(post("/api/notifications/{notificationId}/read", adminNotificationId.longValue())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + (adminNotificationVersion.longValue() + 1) + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_NOTIFICATION_VERSION_CONFLICT"));

        mockMvc.perform(post("/api/notifications/{notificationId}/read", adminNotificationId.longValue())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + adminNotificationVersion.longValue() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readAt").isNotEmpty())
                .andExpect(jsonPath("$.version").value(adminNotificationVersion.longValue() + 1));

        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));

        mockMvc.perform(post("/api/customer/notifications/read-all")
                        .header("Authorization", bearer(customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(1))
                .andExpect(jsonPath("$.readAt").isNotEmpty());

        mockMvc.perform(get("/api/customer/notifications")
                        .header("Authorization", bearer(customerToken))
                        .param("unreadOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.unreadCount").value(0));
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
}
