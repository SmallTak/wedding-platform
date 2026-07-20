package com.wedding.platform.operations.feedback.application;

import com.wedding.platform.content.collection.persistence.entity.CollectionCreator;
import com.wedding.platform.content.collection.persistence.entity.WorkCollection;
import com.wedding.platform.content.collection.persistence.repository.CollectionCreatorRepository;
import com.wedding.platform.content.collection.persistence.repository.WorkCollectionRepository;
import com.wedding.platform.content.shared.ContentVisibility;
import com.wedding.platform.content.shared.PublishStatus;
import com.wedding.platform.operations.feedback.persistence.entity.CustomerFeedback;
import com.wedding.platform.operations.feedback.persistence.entity.FeedbackPublishStatus;
import com.wedding.platform.operations.feedback.persistence.entity.FeedbackReply;
import com.wedding.platform.operations.feedback.persistence.entity.FeedbackReviewStatus;
import com.wedding.platform.operations.feedback.persistence.repository.CustomerFeedbackRepository;
import com.wedding.platform.operations.feedback.persistence.repository.FeedbackReplyRepository;
import com.wedding.platform.operations.feedback.web.FeedbackDtos;
import com.wedding.platform.operations.notification.application.UserNotificationService;
import com.wedding.platform.operations.notification.persistence.entity.UserNotificationRelatedType;
import com.wedding.platform.operations.notification.persistence.entity.UserNotificationType;
import com.wedding.platform.platform.audit.AuditLogService;
import com.wedding.platform.platform.web.ApiException;
import com.wedding.platform.system.account.persistence.entity.ProfessionalRole;
import com.wedding.platform.system.account.persistence.entity.SystemUser;
import com.wedding.platform.system.account.persistence.repository.SystemUserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class FeedbackService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int OPTIONS_LIMIT = 500;

    private final CustomerFeedbackRepository feedbackRepository;
    private final FeedbackReplyRepository replyRepository;
    private final WorkCollectionRepository collectionRepository;
    private final CollectionCreatorRepository collectionCreatorRepository;
    private final SystemUserRepository userRepository;
    private final UserNotificationService notificationService;
    private final AuditLogService auditLogService;

    public FeedbackService(
            CustomerFeedbackRepository feedbackRepository,
            FeedbackReplyRepository replyRepository,
            WorkCollectionRepository collectionRepository,
            CollectionCreatorRepository collectionCreatorRepository,
            SystemUserRepository userRepository,
            UserNotificationService notificationService,
            AuditLogService auditLogService
    ) {
        this.feedbackRepository = feedbackRepository;
        this.replyRepository = replyRepository;
        this.collectionRepository = collectionRepository;
        this.collectionCreatorRepository = collectionCreatorRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public FeedbackDtos.FeedbackPage list(
            Long userId,
            int page,
            int size,
            FeedbackReviewStatus reviewStatus,
            FeedbackPublishStatus publishStatus,
            Long collectionId
    ) {
        validatePage(page, size);
        requireAdmin(userId);
        Page<CustomerFeedback> result = feedbackRepository.findAllFeedback(
                reviewStatus,
                publishStatus,
                collectionId,
                FeedbackReviewStatus.PENDING,
                FeedbackReviewStatus.REJECTED,
                PageRequest.of(page, size));
        return new FeedbackDtos.FeedbackPage(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public FeedbackDtos.FeedbackOptions options(Long userId) {
        requireAdmin(userId);
        List<WorkCollection> collections = collectionRepository.findAllCollections(
                        null,
                        null,
                        PageRequest.of(0, OPTIONS_LIMIT))
                .getContent();
        List<SystemUser> creators = userRepository.findAllByAccountTypeAndDeletedFalseOrderByCreatedAtDesc("CREATOR")
                .stream()
                .filter(user -> "ACTIVE".equals(user.getAccountStatus()))
                .toList();
        return new FeedbackDtos.FeedbackOptions(
                collections.stream().map(this::collectionSummary).toList(),
                creators.stream().map(this::creatorSummary).toList()
        );
    }

    @Transactional(readOnly = true)
    public FeedbackDtos.FeedbackPage customerList(
            Long customerId,
            int page,
            int size,
            FeedbackReviewStatus reviewStatus,
            FeedbackPublishStatus publishStatus
    ) {
        validatePage(page, size);
        SystemUser customer = getActor(customerId);
        if (!"CUSTOMER".equals(customer.getAccountType())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CUSTOMER_ACCOUNT_REQUIRED",
                    "Only customer accounts can view customer feedback");
        }
        Page<CustomerFeedback> result = feedbackRepository.findCustomerFeedback(
                customerId,
                reviewStatus,
                publishStatus,
                PageRequest.of(page, size));
        return new FeedbackDtos.FeedbackPage(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional
    public FeedbackDtos.FeedbackResponse create(
            Long userId,
            FeedbackDtos.UpsertFeedbackRequest request,
            String ipAddress
    ) {
        SystemUser admin = requireAdmin(userId);
        WorkCollection collection = getCollection(request.collectionId());
        SystemUser creator = getCreator(request.creatorUserId());
        requireCreatorInCollection(collection, creator);

        CustomerFeedback feedback = new CustomerFeedback();
        apply(feedback, request);
        feedback.setSubmittedBy(userId);
        feedback.setReviewStatus(FeedbackReviewStatus.PENDING);
        feedback.setPublishStatus(FeedbackPublishStatus.UNPUBLISHED);
        feedback.setCreatedBy(userId);
        feedback.setUpdatedBy(userId);
        feedback = feedbackRepository.save(feedback);

        notifyNewFeedback(userId, feedback, "新的客户评价待审核");
        auditLogService.record(
                userId,
                admin.getAccountType(),
                "FEEDBACK",
                "CREATE_FEEDBACK",
                "CUSTOMER_FEEDBACK",
                feedback.getId(),
                "Customer feedback submitted for review",
                ipAddress
        );
        return toResponse(feedback);
    }

    @Transactional
    public FeedbackDtos.FeedbackResponse update(
            Long userId,
            Long feedbackId,
            FeedbackDtos.UpsertFeedbackRequest request,
            String ipAddress
    ) {
        SystemUser admin = requireAdmin(userId);
        CustomerFeedback feedback = getFeedback(feedbackId);
        requireVersion(feedback.getVersion(), request.version(), "FEEDBACK_VERSION_CONFLICT");
        if (FeedbackPublishStatus.PUBLISHED == feedback.getPublishStatus()
                || FeedbackPublishStatus.OFFLINE == feedback.getPublishStatus()) {
            throw new ApiException(HttpStatus.CONFLICT, "FEEDBACK_PUBLISHED_LOCKED",
                    "Published or offline feedback cannot be edited");
        }

        WorkCollection collection = getCollection(request.collectionId());
        SystemUser creator = getCreator(request.creatorUserId());
        requireCreatorInCollection(collection, creator);
        apply(feedback, request);
        feedback.setReviewStatus(FeedbackReviewStatus.PENDING);
        feedback.setRejectionReason(null);
        feedback.setReviewedBy(null);
        feedback.setReviewedAt(null);
        feedback.setUpdatedBy(userId);
        feedback = feedbackRepository.save(feedback);

        notifyNewFeedback(userId, feedback, "客户评价已重新提交审核");
        auditLogService.record(
                userId,
                admin.getAccountType(),
                "FEEDBACK",
                "UPDATE_FEEDBACK",
                "CUSTOMER_FEEDBACK",
                feedbackId,
                "Customer feedback updated and resubmitted",
                ipAddress
        );
        return toResponse(feedback);
    }

    @Transactional
    public void withdraw(
            Long userId,
            Long feedbackId,
            Long version,
            String ipAddress
    ) {
        SystemUser admin = requireAdmin(userId);
        CustomerFeedback feedback = getFeedback(feedbackId);
        requireVersion(feedback.getVersion(), version, "FEEDBACK_VERSION_CONFLICT");
        if (FeedbackPublishStatus.UNPUBLISHED != feedback.getPublishStatus()) {
            throw new ApiException(HttpStatus.CONFLICT, "FEEDBACK_WITHDRAW_LOCKED",
                    "Published or offline feedback cannot be withdrawn");
        }

        Instant now = Instant.now();
        feedback.setDeleted(true);
        feedback.setDeletedAt(now);
        feedback.setUpdatedBy(userId);
        feedbackRepository.save(feedback);

        replyRepository.findByFeedbackIdAndDeletedFalse(feedbackId).ifPresent(reply -> {
            reply.setDeleted(true);
            reply.setDeletedAt(now);
            reply.setUpdatedBy(userId);
            replyRepository.save(reply);
        });

        auditLogService.record(
                userId,
                admin.getAccountType(),
                "FEEDBACK",
                "WITHDRAW_FEEDBACK",
                "CUSTOMER_FEEDBACK",
                feedbackId,
                "Customer feedback withdrawn before publication",
                ipAddress
        );
    }

    @Transactional
    public FeedbackDtos.FeedbackResponse approve(
            Long adminId,
            Long feedbackId,
            FeedbackDtos.VersionRequest request,
            String ipAddress
    ) {
        SystemUser admin = requireAdmin(adminId);
        CustomerFeedback feedback = getFeedback(feedbackId);
        requireVersion(feedback.getVersion(), request.version(), "FEEDBACK_VERSION_CONFLICT");
        requirePending(feedback);
        WorkCollection collection = getCollection(feedback.getCollectionId());
        if (PublishStatus.PUBLISHED != collection.getPublishStatus()
                || ContentVisibility.PUBLIC != collection.getVisibility()) {
            throw new ApiException(HttpStatus.CONFLICT, "FEEDBACK_COLLECTION_NOT_PUBLIC",
                    "Feedback can only be published for a public work collection");
        }

        Instant now = Instant.now();
        feedback.setReviewStatus(FeedbackReviewStatus.APPROVED);
        feedback.setRejectionReason(null);
        feedback.setReviewedBy(adminId);
        feedback.setReviewedAt(now);
        feedback.setPublishStatus(FeedbackPublishStatus.PUBLISHED);
        feedback.setPublishedAt(now);
        feedback.setOfflineReason(null);
        feedback.setUpdatedBy(adminId);
        feedback = feedbackRepository.save(feedback);

        notifyFeedbackCreator(feedback, adminId, UserNotificationType.FEEDBACK_APPROVED,
                "客户评价已通过审核", "客户评价已通过审核并在官网公开展示。");
        auditLogService.record(
                adminId,
                admin.getAccountType(),
                "FEEDBACK",
                "APPROVE_FEEDBACK",
                "CUSTOMER_FEEDBACK",
                feedbackId,
                "Customer feedback approved and published",
                ipAddress
        );
        return toResponse(feedback);
    }

    @Transactional
    public FeedbackDtos.FeedbackResponse reject(
            Long adminId,
            Long feedbackId,
            FeedbackDtos.RejectRequest request,
            String ipAddress
    ) {
        SystemUser admin = requireAdmin(adminId);
        CustomerFeedback feedback = getFeedback(feedbackId);
        requireVersion(feedback.getVersion(), request.version(), "FEEDBACK_VERSION_CONFLICT");
        requirePending(feedback);
        feedback.setReviewStatus(FeedbackReviewStatus.REJECTED);
        feedback.setRejectionReason(request.reason().trim());
        feedback.setReviewedBy(adminId);
        feedback.setReviewedAt(Instant.now());
        feedback.setPublishStatus(FeedbackPublishStatus.UNPUBLISHED);
        feedback.setUpdatedBy(adminId);
        feedback = feedbackRepository.save(feedback);

        notifyFeedbackCreator(feedback, adminId, UserNotificationType.FEEDBACK_REJECTED,
                "客户评价未通过审核", "客户评价未通过审核。原因：" + feedback.getRejectionReason());
        auditLogService.record(
                adminId,
                admin.getAccountType(),
                "FEEDBACK",
                "REJECT_FEEDBACK",
                "CUSTOMER_FEEDBACK",
                feedbackId,
                request.reason().trim(),
                ipAddress
        );
        return toResponse(feedback);
    }

    @Transactional
    public FeedbackDtos.FeedbackResponse offline(
            Long adminId,
            Long feedbackId,
            FeedbackDtos.OfflineRequest request,
            String ipAddress
    ) {
        SystemUser admin = requireAdmin(adminId);
        CustomerFeedback feedback = getFeedback(feedbackId);
        requireVersion(feedback.getVersion(), request.version(), "FEEDBACK_VERSION_CONFLICT");
        if (FeedbackPublishStatus.PUBLISHED != feedback.getPublishStatus()) {
            throw new ApiException(HttpStatus.CONFLICT, "FEEDBACK_NOT_PUBLISHED",
                    "Only published feedback can be taken offline");
        }
        feedback.setPublishStatus(FeedbackPublishStatus.OFFLINE);
        feedback.setOfflineReason(request.reason().trim());
        feedback.setUpdatedBy(adminId);
        feedback = feedbackRepository.save(feedback);

        notifyFeedbackCreator(feedback, adminId, UserNotificationType.FEEDBACK_OFFLINE,
                "客户评价已下架", "该客户评价已从官网下架。原因：" + feedback.getOfflineReason());
        auditLogService.record(
                adminId,
                admin.getAccountType(),
                "FEEDBACK",
                "OFFLINE_FEEDBACK",
                "CUSTOMER_FEEDBACK",
                feedbackId,
                request.reason().trim(),
                ipAddress
        );
        return toResponse(feedback);
    }

    @Transactional
    public FeedbackDtos.FeedbackResponse upsertReply(
            Long adminId,
            Long feedbackId,
            FeedbackDtos.UpsertReplyRequest request,
            String ipAddress
    ) {
        SystemUser admin = requireAdmin(adminId);
        CustomerFeedback feedback = getFeedback(feedbackId);
        if (FeedbackPublishStatus.PUBLISHED != feedback.getPublishStatus()) {
            throw new ApiException(HttpStatus.CONFLICT, "FEEDBACK_REPLY_NOT_AVAILABLE",
                    "A reply can only be saved for published feedback");
        }

        Instant now = Instant.now();
        FeedbackReply reply = replyRepository.findByFeedbackIdAndDeletedFalse(feedbackId).orElse(null);
        if (reply == null) {
            if (request.version() != null) {
                throw new ApiException(HttpStatus.CONFLICT, "FEEDBACK_REPLY_VERSION_CONFLICT",
                        "Reload the feedback before saving the reply");
            }
            reply = new FeedbackReply();
            reply.setFeedbackId(feedbackId);
            reply.setCreatorUserId(feedback.getCreatorUserId());
            reply.setCreatedBy(adminId);
        } else {
            requireVersion(reply.getVersion(), request.version(), "FEEDBACK_REPLY_VERSION_CONFLICT");
        }
        reply.setContent(request.content().trim());
        reply.setReviewStatus(FeedbackReviewStatus.APPROVED);
        reply.setRejectionReason(null);
        reply.setReviewedBy(adminId);
        reply.setReviewedAt(now);
        reply.setPublishedAt(now);
        reply.setUpdatedBy(adminId);
        replyRepository.save(reply);

        auditLogService.record(
                adminId,
                admin.getAccountType(),
                "FEEDBACK",
                "UPSERT_FEEDBACK_REPLY",
                "FEEDBACK_REPLY",
                reply.getId(),
                "Feedback reply saved by administrator",
                ipAddress
        );
        return toResponse(feedback);
    }

    @Transactional
    public FeedbackDtos.FeedbackResponse approveReply(
            Long adminId,
            Long feedbackId,
            FeedbackDtos.VersionRequest request,
            String ipAddress
    ) {
        SystemUser admin = requireAdmin(adminId);
        CustomerFeedback feedback = getFeedback(feedbackId);
        if (FeedbackPublishStatus.PUBLISHED != feedback.getPublishStatus()) {
            throw new ApiException(HttpStatus.CONFLICT, "FEEDBACK_REPLY_NOT_AVAILABLE",
                    "The parent feedback is not published");
        }
        FeedbackReply reply = getReply(feedbackId);
        requireVersion(reply.getVersion(), request.version(), "FEEDBACK_REPLY_VERSION_CONFLICT");
        requirePending(reply);
        reply.setReviewStatus(FeedbackReviewStatus.APPROVED);
        reply.setRejectionReason(null);
        reply.setReviewedBy(adminId);
        reply.setReviewedAt(Instant.now());
        reply.setPublishedAt(Instant.now());
        reply.setUpdatedBy(adminId);
        replyRepository.save(reply);

        notificationService.notifyUser(
                reply.getCreatorUserId(),
                adminId,
                UserNotificationType.FEEDBACK_REPLY_APPROVED,
                "评价回复已通过审核",
                "您提交的评价回复已通过审核并在官网展示。",
                UserNotificationRelatedType.FEEDBACK_REPLY,
                reply.getId()
        );
        auditLogService.record(
                adminId,
                admin.getAccountType(),
                "FEEDBACK",
                "APPROVE_FEEDBACK_REPLY",
                "FEEDBACK_REPLY",
                reply.getId(),
                "Feedback reply approved and published",
                ipAddress
        );
        return toResponse(feedback);
    }

    @Transactional
    public FeedbackDtos.FeedbackResponse rejectReply(
            Long adminId,
            Long feedbackId,
            FeedbackDtos.RejectRequest request,
            String ipAddress
    ) {
        SystemUser admin = requireAdmin(adminId);
        CustomerFeedback feedback = getFeedback(feedbackId);
        FeedbackReply reply = getReply(feedbackId);
        requireVersion(reply.getVersion(), request.version(), "FEEDBACK_REPLY_VERSION_CONFLICT");
        requirePending(reply);
        reply.setReviewStatus(FeedbackReviewStatus.REJECTED);
        reply.setRejectionReason(request.reason().trim());
        reply.setReviewedBy(adminId);
        reply.setReviewedAt(Instant.now());
        reply.setPublishedAt(null);
        reply.setUpdatedBy(adminId);
        replyRepository.save(reply);

        notificationService.notifyUser(
                reply.getCreatorUserId(),
                adminId,
                UserNotificationType.FEEDBACK_REPLY_REJECTED,
                "评价回复未通过审核",
                "您提交的评价回复未通过审核。原因：" + reply.getRejectionReason(),
                UserNotificationRelatedType.FEEDBACK_REPLY,
                reply.getId()
        );
        auditLogService.record(
                adminId,
                admin.getAccountType(),
                "FEEDBACK",
                "REJECT_FEEDBACK_REPLY",
                "FEEDBACK_REPLY",
                reply.getId(),
                request.reason().trim(),
                ipAddress
        );
        return toResponse(feedback);
    }

    private void apply(CustomerFeedback feedback, FeedbackDtos.UpsertFeedbackRequest request) {
        feedback.setCollectionId(request.collectionId());
        feedback.setCreatorUserId(request.creatorUserId());
        feedback.setCustomerDisplayName(request.customerDisplayName().trim());
        feedback.setRating(request.rating());
        feedback.setContent(request.content().trim());
    }

    private void notifyNewFeedback(Long actorId, CustomerFeedback feedback, String title) {
        notificationService.notifyAdmins(
                actorId,
                UserNotificationType.CUSTOMER_FEEDBACK_NEW,
                title,
                "收到一条新的客户评价，请及时审核。",
                UserNotificationRelatedType.FEEDBACK,
                feedback.getId()
        );
        if (!actorId.equals(feedback.getCreatorUserId())) {
            notificationService.notifyUser(
                    feedback.getCreatorUserId(),
                    actorId,
                    UserNotificationType.CUSTOMER_FEEDBACK_NEW,
                    "收到新的客户评价",
                    "有客户向您提交了新的评价，审核完成后会在官网展示。",
                    UserNotificationRelatedType.FEEDBACK,
                    feedback.getId()
            );
        }
    }

    private void notifyFeedbackCreator(
            CustomerFeedback feedback,
            Long actorId,
            UserNotificationType type,
            String title,
            String content
    ) {
        if (!actorId.equals(feedback.getCreatorUserId())) {
            notificationService.notifyUser(
                    feedback.getCreatorUserId(),
                    actorId,
                    type,
                    title,
                    content,
                    UserNotificationRelatedType.FEEDBACK,
                    feedback.getId()
            );
        }
    }

    private FeedbackDtos.FeedbackResponse toResponse(CustomerFeedback feedback) {
        WorkCollection collection = collectionRepository.findById(feedback.getCollectionId()).orElse(null);
        SystemUser creator = userRepository.findById(feedback.getCreatorUserId()).orElse(null);
        FeedbackReply reply = replyRepository.findByFeedbackIdAndDeletedFalse(feedback.getId()).orElse(null);
        return new FeedbackDtos.FeedbackResponse(
                feedback.getId(),
                collection == null ? null : collectionSummary(collection),
                creator == null ? null : creatorSummary(creator),
                feedback.getCustomerDisplayName(),
                feedback.getRating(),
                feedback.getContent(),
                feedback.getReviewStatus(),
                feedback.getRejectionReason(),
                feedback.getReviewedBy(),
                feedback.getReviewedAt(),
                feedback.getPublishStatus(),
                feedback.getPublishedAt(),
                feedback.getOfflineReason(),
                feedback.getSubmittedBy(),
                feedback.getCreatedAt(),
                feedback.getUpdatedAt(),
                feedback.getVersion(),
                reply == null ? null : replyResponse(reply)
        );
    }

    private FeedbackDtos.CollectionSummary collectionSummary(WorkCollection collection) {
        return new FeedbackDtos.CollectionSummary(
                collection.getId(),
                collection.getTitle(),
                collection.getEventDate(),
                collection.getLocationText(),
                collectionCreatorIds(collection)
        );
    }

    private List<Long> collectionCreatorIds(WorkCollection collection) {
        LinkedHashSet<Long> creatorIds = new LinkedHashSet<>();
        userRepository.findById(collection.getCreatedBy())
                .filter(this::isCreator)
                .ifPresent(user -> creatorIds.add(user.getId()));
        collectionCreatorRepository.findAllByCollectionId(collection.getId()).stream()
                .map(CollectionCreator::getId)
                .map(id -> id.getCreatorUserId())
                .forEach(creatorIds::add);
        return List.copyOf(creatorIds);
    }

    private FeedbackDtos.CreatorSummary creatorSummary(SystemUser creator) {
        return new FeedbackDtos.CreatorSummary(
                creator.getId(),
                creator.getDisplayName(),
                creator.getProfessionalRoles().stream()
                        .filter(role -> !Boolean.TRUE.equals(role.getDeleted()))
                        .sorted(Comparator.comparing(ProfessionalRole::getSortOrder))
                        .map(ProfessionalRole::getName)
                        .toList()
        );
    }

    private FeedbackDtos.ReplyResponse replyResponse(FeedbackReply reply) {
        return new FeedbackDtos.ReplyResponse(
                reply.getId(),
                reply.getContent(),
                reply.getReviewStatus(),
                reply.getRejectionReason(),
                reply.getReviewedBy(),
                reply.getReviewedAt(),
                reply.getPublishedAt(),
                reply.getCreatedAt(),
                reply.getUpdatedAt(),
                reply.getVersion()
        );
    }

    private void requireCreatorInCollection(WorkCollection collection, SystemUser creator) {
        if (collectionCreatorIds(collection).contains(creator.getId())) {
            return;
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "FEEDBACK_CREATOR_NOT_IN_COLLECTION",
                "The reviewed creator does not participate in this work collection");
    }

    private void requirePending(CustomerFeedback feedback) {
        if (FeedbackReviewStatus.PENDING != feedback.getReviewStatus()) {
            throw new ApiException(HttpStatus.CONFLICT, "FEEDBACK_NOT_PENDING",
                    "Only pending feedback can be reviewed");
        }
    }

    private void requirePending(FeedbackReply reply) {
        if (FeedbackReviewStatus.PENDING != reply.getReviewStatus()) {
            throw new ApiException(HttpStatus.CONFLICT, "FEEDBACK_REPLY_NOT_PENDING",
                    "Only a pending reply can be reviewed");
        }
    }

    private CustomerFeedback getFeedback(Long feedbackId) {
        return feedbackRepository.findByIdAndDeletedFalse(feedbackId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FEEDBACK_NOT_FOUND",
                        "Customer feedback was not found"));
    }

    private FeedbackReply getReply(Long feedbackId) {
        return replyRepository.findByFeedbackIdAndDeletedFalse(feedbackId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FEEDBACK_REPLY_NOT_FOUND",
                        "Feedback reply was not found"));
    }

    private WorkCollection getCollection(Long collectionId) {
        return collectionRepository.findByIdAndDeletedFalse(collectionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COLLECTION_NOT_FOUND",
                        "Work collection was not found"));
    }

    private SystemUser getCreator(Long creatorId) {
        SystemUser creator = getActor(creatorId);
        if (!isCreator(creator)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FEEDBACK_CREATOR_INVALID",
                    "The reviewed account must be a creator");
        }
        return creator;
    }

    private SystemUser getActor(Long userId) {
        return userRepository.findById(userId)
                .filter(user -> !Boolean.TRUE.equals(user.getDeleted()))
                .filter(user -> "ACTIVE".equals(user.getAccountStatus()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_NOT_FOUND",
                        "Account is not available"));
    }

    private SystemUser requireAdmin(Long userId) {
        SystemUser actor = getActor(userId);
        if (!"ADMIN".equals(actor.getAccountType())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FEEDBACK_ACCESS_DENIED",
                    "Only administrators can manage customer feedback");
        }
        return actor;
    }

    private void requireVersion(Long currentVersion, Long requestVersion, String code) {
        if (requestVersion == null || !currentVersion.equals(requestVersion)) {
            throw new ApiException(HttpStatus.CONFLICT, code,
                    "The record was updated by another user; reload it before saving");
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAGE_INVALID",
                    "Page must be at least 0 and size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    private boolean isCreator(SystemUser user) {
        return "CREATOR".equals(user.getAccountType());
    }
}
