package com.wedding.platform.operations.feedback.application;

import com.wedding.platform.content.collection.persistence.entity.WorkCollection;
import com.wedding.platform.content.collection.persistence.repository.WorkCollectionRepository;
import com.wedding.platform.content.shared.ContentVisibility;
import com.wedding.platform.content.shared.PublishStatus;
import com.wedding.platform.operations.feedback.persistence.entity.CustomerFeedback;
import com.wedding.platform.operations.feedback.persistence.entity.FeedbackPublishStatus;
import com.wedding.platform.operations.feedback.persistence.entity.FeedbackReply;
import com.wedding.platform.operations.feedback.persistence.entity.FeedbackReviewStatus;
import com.wedding.platform.operations.feedback.persistence.repository.CustomerFeedbackRepository;
import com.wedding.platform.operations.feedback.persistence.repository.FeedbackReplyRepository;
import com.wedding.platform.operations.feedback.web.PublicFeedbackDtos;
import com.wedding.platform.platform.web.ApiException;
import com.wedding.platform.system.account.persistence.entity.ProfessionalRole;
import com.wedding.platform.system.account.persistence.entity.SystemUser;
import com.wedding.platform.system.account.persistence.repository.SystemUserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PublicFeedbackService {

    private static final int MAX_PAGE_SIZE = 60;

    private final CustomerFeedbackRepository feedbackRepository;
    private final FeedbackReplyRepository replyRepository;
    private final WorkCollectionRepository collectionRepository;
    private final SystemUserRepository userRepository;

    public PublicFeedbackService(
            CustomerFeedbackRepository feedbackRepository,
            FeedbackReplyRepository replyRepository,
            WorkCollectionRepository collectionRepository,
            SystemUserRepository userRepository
    ) {
        this.feedbackRepository = feedbackRepository;
        this.replyRepository = replyRepository;
        this.collectionRepository = collectionRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PublicFeedbackDtos.FeedbackPage list(int page, int size) {
        validatePage(page, size);
        Page<CustomerFeedback> result = feedbackRepository.findPublicFeedback(
                FeedbackReviewStatus.APPROVED,
                FeedbackPublishStatus.PUBLISHED,
                PublishStatus.PUBLISHED,
                ContentVisibility.PUBLIC,
                PageRequest.of(page, size)
        );
        Map<Long, FeedbackReply> replies = replies(result.getContent());
        return new PublicFeedbackDtos.FeedbackPage(
                result.getContent().stream()
                        .map(feedback -> toPublic(feedback, replies.get(feedback.getId())))
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public List<PublicFeedbackDtos.Feedback> collectionFeedback(Long collectionId) {
        List<CustomerFeedback> feedback = feedbackRepository.findPublicFeedbackByCollection(
                collectionId,
                FeedbackReviewStatus.APPROVED,
                FeedbackPublishStatus.PUBLISHED,
                PublishStatus.PUBLISHED,
                ContentVisibility.PUBLIC
        );
        Map<Long, FeedbackReply> replies = replies(feedback);
        return feedback.stream().map(item -> toPublic(item, replies.get(item.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public List<PublicFeedbackDtos.Feedback> latest(int size) {
        int limitedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return list(0, limitedSize).content();
    }

    @Transactional(readOnly = true)
    public List<PublicFeedbackDtos.Feedback> byIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, CustomerFeedback> feedbackById = new LinkedHashMap<>();
        feedbackRepository.findAllById(ids).stream()
                .filter(this::isPublic)
                .forEach(feedback -> feedbackById.put(feedback.getId(), feedback));
        List<CustomerFeedback> ordered = ids.stream()
                .map(feedbackById::get)
                .filter(feedback -> feedback != null)
                .toList();
        Map<Long, FeedbackReply> replies = replies(ordered);
        return ordered.stream().map(item -> toPublic(item, replies.get(item.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public boolean isPublicFeedback(Long feedbackId) {
        return feedbackRepository.findByIdAndDeletedFalse(feedbackId).filter(this::isPublic).isPresent();
    }

    private boolean isPublic(CustomerFeedback feedback) {
        if (FeedbackReviewStatus.APPROVED != feedback.getReviewStatus()
                || FeedbackPublishStatus.PUBLISHED != feedback.getPublishStatus()) {
            return false;
        }
        return collectionRepository.findByIdAndDeletedFalse(feedback.getCollectionId())
                .filter(collection -> PublishStatus.PUBLISHED == collection.getPublishStatus())
                .filter(collection -> ContentVisibility.PUBLIC == collection.getVisibility())
                .isPresent();
    }

    private PublicFeedbackDtos.Feedback toPublic(CustomerFeedback feedback, FeedbackReply reply) {
        WorkCollection collection = collectionRepository.findById(feedback.getCollectionId()).orElse(null);
        SystemUser creator = userRepository.findById(feedback.getCreatorUserId()).orElse(null);
        List<String> roles = creator == null
                ? List.of()
                : creator.getProfessionalRoles().stream()
                        .filter(role -> !Boolean.TRUE.equals(role.getDeleted()))
                        .sorted(Comparator.comparing(ProfessionalRole::getSortOrder))
                        .map(ProfessionalRole::getName)
                        .toList();
        PublicFeedbackDtos.Reply publicReply = reply != null
                && FeedbackReviewStatus.APPROVED == reply.getReviewStatus()
                && reply.getPublishedAt() != null
                ? new PublicFeedbackDtos.Reply(reply.getContent(), reply.getPublishedAt())
                : null;
        return new PublicFeedbackDtos.Feedback(
                feedback.getId(),
                feedback.getCollectionId(),
                collection == null ? null : collection.getTitle(),
                feedback.getCreatorUserId(),
                creator == null ? null : creator.getDisplayName(),
                roles,
                maskName(feedback.getCustomerDisplayName()),
                feedback.getRating(),
                feedback.getContent(),
                feedback.getPublishedAt(),
                publicReply
        );
    }

    private Map<Long, FeedbackReply> replies(List<CustomerFeedback> feedback) {
        if (feedback.isEmpty()) {
            return Map.of();
        }
        Map<Long, FeedbackReply> replies = new LinkedHashMap<>();
        replyRepository.findAllByFeedbackIdInAndDeletedFalse(
                        feedback.stream().map(CustomerFeedback::getId).toList())
                .forEach(reply -> replies.put(reply.getFeedbackId(), reply));
        return replies;
    }

    private String maskName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            return "匿名客户";
        }
        if (trimmed.length() == 1) {
            return trimmed + "*";
        }
        return trimmed.substring(0, 1) + "**";
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAGE_INVALID",
                    "Page must be at least 0 and size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }
}
