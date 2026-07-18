package com.wedding.platform.content.project.application;

import com.wedding.platform.content.project.persistence.entity.ProjectCustomerApplication;
import com.wedding.platform.content.project.persistence.entity.WeddingProject;
import com.wedding.platform.content.project.persistence.repository.ProjectCustomerApplicationRepository;
import com.wedding.platform.content.project.persistence.repository.WeddingProjectRepository;
import com.wedding.platform.content.project.web.CustomerProjectDtos;
import com.wedding.platform.content.shared.ContentVisibility;
import com.wedding.platform.content.shared.PublishStatus;
import com.wedding.platform.operations.notification.application.UserNotificationService;
import com.wedding.platform.operations.notification.persistence.entity.UserNotificationRelatedType;
import com.wedding.platform.operations.notification.persistence.entity.UserNotificationType;
import com.wedding.platform.platform.audit.AuditLogService;
import com.wedding.platform.platform.web.ApiException;
import com.wedding.platform.system.account.persistence.entity.CustomerProfile;
import com.wedding.platform.system.account.persistence.entity.SystemUser;
import com.wedding.platform.system.account.persistence.repository.CustomerProfileRepository;
import com.wedding.platform.system.account.persistence.repository.SystemUserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

@Service
public class CustomerProjectService {

    private static final String PENDING = "PENDING";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";
    private static final int MAX_PAGE_SIZE = 100;

    private final ProjectCustomerApplicationRepository applicationRepository;
    private final WeddingProjectRepository projectRepository;
    private final SystemUserRepository userRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final UserNotificationService notificationService;
    private final AuditLogService auditLogService;

    public CustomerProjectService(
            ProjectCustomerApplicationRepository applicationRepository,
            WeddingProjectRepository projectRepository,
            SystemUserRepository userRepository,
            CustomerProfileRepository customerProfileRepository,
            UserNotificationService notificationService,
            AuditLogService auditLogService
    ) {
        this.applicationRepository = applicationRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public CustomerProjectDtos.ApplicationResponse apply(
            Long customerId,
            CustomerProjectDtos.ApplyRequest request,
            String ipAddress
    ) {
        SystemUser customer = requireCustomer(customerId);
        WeddingProject project = projectRepository.findByProjectCodeAndDeletedFalse(
                        request.projectCode().trim())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_CODE_NOT_FOUND",
                        "No wedding project matches this project code"));

        ProjectCustomerApplication application = applicationRepository
                .findByProjectIdAndCustomerUserIdAndDeletedFalse(project.getId(), customerId)
                .orElse(null);
        if (application == null) {
            if (request.version() != null) {
                throw versionConflict();
            }
            application = new ProjectCustomerApplication();
            application.setProjectId(project.getId());
            application.setCustomerUserId(customerId);
            application.setCreatedBy(customerId);
        } else {
            if (PENDING.equals(application.getStatus())) {
                throw new ApiException(HttpStatus.CONFLICT, "CUSTOMER_PROJECT_APPLICATION_PENDING",
                        "This project application is already pending");
            }
            if (APPROVED.equals(application.getStatus())) {
                throw new ApiException(HttpStatus.CONFLICT, "CUSTOMER_PROJECT_ALREADY_LINKED",
                        "This customer is already linked to the project");
            }
            requireVersion(application.getVersion(), request.version());
        }

        application.setApplyNote(request.applyNote().trim());
        application.setStatus(PENDING);
        application.setReviewedBy(null);
        application.setReviewedAt(null);
        application.setRejectionReason(null);
        application.setUpdatedBy(customerId);
        application.setDeleted(false);
        application.setDeletedAt(null);
        application = applicationRepository.save(application);

        notificationService.notifyAdmins(
                customerId,
                UserNotificationType.CUSTOMER_PROJECT_APPLICATION_NEW,
                "新的客户项目关联申请",
                "客户提交了项目编号 " + project.getProjectCode() + " 的关联申请，请及时审核。",
                UserNotificationRelatedType.PROJECT_APPLICATION,
                application.getId()
        );
        auditLogService.record(
                customerId,
                customer.getAccountType(),
                "CUSTOMER_PROJECT",
                "APPLY_PROJECT_LINK",
                "PROJECT_CUSTOMER_APPLICATION",
                application.getId(),
                "Customer applied to link wedding project " + project.getProjectCode(),
                ipAddress
        );
        return toResponse(application, true);
    }

    @Transactional(readOnly = true)
    public List<CustomerProjectDtos.ApplicationResponse> customerApplications(Long customerId) {
        requireCustomer(customerId);
        return applicationRepository
                .findAllByCustomerUserIdAndDeletedFalseOrderByCreatedAtDesc(customerId)
                .stream()
                .map(application -> toResponse(application, true))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CustomerProjectDtos.ApplicationResponse> linkedProjects(Long customerId) {
        requireCustomer(customerId);
        return applicationRepository
                .findAllByCustomerUserIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(customerId, APPROVED)
                .stream()
                .map(application -> toResponse(application, true))
                .toList();
    }

    @Transactional(readOnly = true)
    public CustomerProjectDtos.ApplicationPage adminApplications(
            Long adminId,
            int page,
            int size,
            String status
    ) {
        requireAdmin(adminId);
        validatePage(page, size);
        String normalizedStatus = normalizeStatus(status);
        Page<ProjectCustomerApplication> result = applicationRepository.findAllForAdmin(
                normalizedStatus,
                PageRequest.of(page, size)
        );
        return new CustomerProjectDtos.ApplicationPage(
                result.getContent().stream()
                        .map(application -> toResponse(application, false))
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional
    public CustomerProjectDtos.ApplicationResponse approve(
            Long adminId,
            Long applicationId,
            CustomerProjectDtos.ReviewRequest request,
            String ipAddress
    ) {
        SystemUser admin = requireAdmin(adminId);
        ProjectCustomerApplication application = requirePending(applicationId, request.version());
        WeddingProject project = projectRepository.findByIdAndDeletedFalse(application.getProjectId())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "CUSTOMER_PROJECT_NOT_AVAILABLE",
                        "The wedding project is no longer available"));

        application.setStatus(APPROVED);
        application.setReviewedBy(adminId);
        application.setReviewedAt(Instant.now());
        application.setRejectionReason(null);
        application.setUpdatedBy(adminId);
        application = applicationRepository.save(application);

        notificationService.notifyProjectLinkApproved(
                application.getCustomerUserId(),
                adminId,
                application.getId(),
                project.getProjectCode()
        );
        auditLogService.record(
                adminId,
                admin.getAccountType(),
                "CUSTOMER_PROJECT",
                "APPROVE_PROJECT_LINK",
                "PROJECT_CUSTOMER_APPLICATION",
                applicationId,
                "Customer linked to wedding project " + project.getProjectCode(),
                ipAddress
        );
        return toResponse(application, false);
    }

    @Transactional
    public CustomerProjectDtos.ApplicationResponse reject(
            Long adminId,
            Long applicationId,
            CustomerProjectDtos.ReviewRequest request,
            String ipAddress
    ) {
        SystemUser admin = requireAdmin(adminId);
        if (!StringUtils.hasText(request.reason())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REJECTION_REASON_REQUIRED",
                    "A rejection reason is required");
        }
        ProjectCustomerApplication application = requirePending(applicationId, request.version());
        application.setStatus(REJECTED);
        application.setReviewedBy(adminId);
        application.setReviewedAt(Instant.now());
        application.setRejectionReason(request.reason().trim());
        application.setUpdatedBy(adminId);
        application = applicationRepository.save(application);

        notificationService.notifyProjectLinkRejected(
                application.getCustomerUserId(),
                adminId,
                application.getId(),
                projectCode(application.getProjectId()),
                application.getRejectionReason()
        );
        auditLogService.record(
                adminId,
                admin.getAccountType(),
                "CUSTOMER_PROJECT",
                "REJECT_PROJECT_LINK",
                "PROJECT_CUSTOMER_APPLICATION",
                applicationId,
                request.reason().trim(),
                ipAddress
        );
        return toResponse(application, false);
    }

    public boolean isLinked(Long projectId, Long customerId) {
        return applicationRepository.existsByProjectIdAndCustomerUserIdAndStatusAndDeletedFalse(
                projectId,
                customerId,
                APPROVED
        );
    }

    private ProjectCustomerApplication requirePending(Long applicationId, Long version) {
        ProjectCustomerApplication application = applicationRepository.findByIdAndDeletedFalse(applicationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "CUSTOMER_PROJECT_APPLICATION_NOT_FOUND",
                        "Customer project application was not found"));
        requireVersion(application.getVersion(), version);
        if (!PENDING.equals(application.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "CUSTOMER_PROJECT_APPLICATION_NOT_PENDING",
                    "Only pending project applications can be reviewed");
        }
        return application;
    }

    private CustomerProjectDtos.ApplicationResponse toResponse(
            ProjectCustomerApplication application,
            boolean maskHiddenDetails
    ) {
        WeddingProject project = projectRepository.findById(application.getProjectId()).orElse(null);
        SystemUser customer = userRepository.findById(application.getCustomerUserId()).orElse(null);
        CustomerProfile profile = customerProfileRepository.findById(application.getCustomerUserId()).orElse(null);
        boolean detailsVisible = project != null
                && !Boolean.TRUE.equals(project.getDeleted())
                && (!maskHiddenDetails || ContentVisibility.HIDDEN != project.getVisibility());
        return new CustomerProjectDtos.ApplicationResponse(
                application.getId(),
                project == null ? null : new CustomerProjectDtos.ProjectSummary(
                        project.getId(),
                        project.getProjectCode(),
                        detailsVisible ? project.getTitle() : null,
                        detailsVisible ? project.getCoupleDisplayName() : null,
                        detailsVisible ? project.getEventDate() : null,
                        detailsVisible ? project.getLocationText() : null,
                        project.getVisibility(),
                        project.getPublishStatus(),
                        detailsVisible,
                        PublishStatus.PUBLISHED == project.getPublishStatus()
                                && ContentVisibility.PUBLIC == project.getVisibility()
                                && !Boolean.TRUE.equals(project.getDeleted())
                ),
                customer == null ? null : new CustomerProjectDtos.CustomerSummary(
                        customer.getId(),
                        customer.getMobile(),
                        profile == null ? customer.getDisplayName() : profile.getNickname()
                ),
                application.getApplyNote(),
                application.getStatus(),
                application.getReviewedBy(),
                application.getReviewedAt(),
                application.getRejectionReason(),
                application.getCreatedAt(),
                application.getUpdatedAt(),
                application.getVersion()
        );
    }

    private SystemUser requireCustomer(Long userId) {
        SystemUser customer = requireActiveUser(userId);
        if (!"CUSTOMER".equals(customer.getAccountType())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CUSTOMER_ACCOUNT_REQUIRED",
                    "A customer account is required");
        }
        return customer;
    }

    private SystemUser requireAdmin(Long userId) {
        SystemUser admin = requireActiveUser(userId);
        if (!"ADMIN".equals(admin.getAccountType())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CUSTOMER_PROJECT_REVIEW_ACCESS_DENIED",
                    "Only administrators can review customer project applications");
        }
        return admin;
    }

    private SystemUser requireActiveUser(Long userId) {
        return userRepository.findById(userId)
                .filter(user -> !Boolean.TRUE.equals(user.getDeleted()))
                .filter(user -> "ACTIVE".equals(user.getAccountStatus()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_NOT_FOUND",
                        "Account is not available"));
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase();
        if (!List.of(PENDING, APPROVED, REJECTED).contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CUSTOMER_PROJECT_STATUS_INVALID",
                    "Application status must be PENDING, APPROVED, or REJECTED");
        }
        return normalized;
    }

    private void requireVersion(Long currentVersion, Long requestVersion) {
        if (requestVersion == null || !currentVersion.equals(requestVersion)) {
            throw versionConflict();
        }
    }

    private ApiException versionConflict() {
        return new ApiException(HttpStatus.CONFLICT, "CUSTOMER_PROJECT_VERSION_CONFLICT",
                "The project application was updated; reload it before continuing");
    }

    private String projectCode(Long projectId) {
        return projectRepository.findById(projectId)
                .map(WeddingProject::getProjectCode)
                .orElse(null);
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAGE_INVALID",
                    "Page must be at least 0 and size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }
}
