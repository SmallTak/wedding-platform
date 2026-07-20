package com.wedding.platform.operations.feedback.persistence.repository;

import com.wedding.platform.content.shared.ContentVisibility;
import com.wedding.platform.content.shared.PublishStatus;
import com.wedding.platform.operations.feedback.persistence.entity.CustomerFeedback;
import com.wedding.platform.operations.feedback.persistence.entity.FeedbackPublishStatus;
import com.wedding.platform.operations.feedback.persistence.entity.FeedbackReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerFeedbackRepository extends JpaRepository<CustomerFeedback, Long> {

    Optional<CustomerFeedback> findByIdAndDeletedFalse(Long id);

    @Query("""
            SELECT feedback
            FROM CustomerFeedback feedback
            WHERE feedback.deleted = false
              AND (:reviewStatus IS NULL OR feedback.reviewStatus = :reviewStatus)
              AND (:publishStatus IS NULL OR feedback.publishStatus = :publishStatus)
              AND (:collectionId IS NULL OR feedback.collectionId = :collectionId)
            ORDER BY
              CASE WHEN feedback.reviewStatus = :pendingStatus THEN 0
                   WHEN feedback.reviewStatus = :rejectedStatus THEN 1
                   ELSE 2 END,
              feedback.createdAt DESC,
              feedback.id DESC
            """)
    Page<CustomerFeedback> findAllFeedback(
            @Param("reviewStatus") FeedbackReviewStatus reviewStatus,
            @Param("publishStatus") FeedbackPublishStatus publishStatus,
            @Param("collectionId") Long collectionId,
            @Param("pendingStatus") FeedbackReviewStatus pendingStatus,
            @Param("rejectedStatus") FeedbackReviewStatus rejectedStatus,
            Pageable pageable
    );

    @Query("""
            SELECT feedback
            FROM CustomerFeedback feedback
            WHERE feedback.deleted = false
              AND (feedback.creatorUserId = :userId OR feedback.submittedBy = :userId)
              AND (:reviewStatus IS NULL OR feedback.reviewStatus = :reviewStatus)
              AND (:publishStatus IS NULL OR feedback.publishStatus = :publishStatus)
              AND (:collectionId IS NULL OR feedback.collectionId = :collectionId)
            ORDER BY
              CASE WHEN feedback.reviewStatus = :pendingStatus THEN 0
                   WHEN feedback.reviewStatus = :rejectedStatus THEN 1
                   ELSE 2 END,
              feedback.createdAt DESC,
              feedback.id DESC
            """)
    Page<CustomerFeedback> findCreatorFeedback(
            @Param("userId") Long userId,
            @Param("reviewStatus") FeedbackReviewStatus reviewStatus,
            @Param("publishStatus") FeedbackPublishStatus publishStatus,
            @Param("collectionId") Long collectionId,
            @Param("pendingStatus") FeedbackReviewStatus pendingStatus,
            @Param("rejectedStatus") FeedbackReviewStatus rejectedStatus,
            Pageable pageable
    );

    @Query("""
            SELECT feedback
            FROM CustomerFeedback feedback
            WHERE feedback.deleted = false
              AND feedback.customerUserId = :customerUserId
              AND (:reviewStatus IS NULL OR feedback.reviewStatus = :reviewStatus)
              AND (:publishStatus IS NULL OR feedback.publishStatus = :publishStatus)
            ORDER BY feedback.createdAt DESC, feedback.id DESC
            """)
    Page<CustomerFeedback> findCustomerFeedback(
            @Param("customerUserId") Long customerUserId,
            @Param("reviewStatus") FeedbackReviewStatus reviewStatus,
            @Param("publishStatus") FeedbackPublishStatus publishStatus,
            Pageable pageable
    );

    @Query("""
            SELECT feedback
            FROM CustomerFeedback feedback, WorkCollection collection
            WHERE feedback.deleted = false
              AND feedback.collectionId = collection.id
              AND feedback.reviewStatus = :reviewStatus
              AND feedback.publishStatus = :feedbackPublishStatus
              AND collection.deleted = false
              AND collection.publishStatus = :collectionPublishStatus
              AND collection.visibility = :visibility
            ORDER BY feedback.publishedAt DESC, feedback.id DESC
            """)
    Page<CustomerFeedback> findPublicFeedback(
            @Param("reviewStatus") FeedbackReviewStatus reviewStatus,
            @Param("feedbackPublishStatus") FeedbackPublishStatus feedbackPublishStatus,
            @Param("collectionPublishStatus") PublishStatus collectionPublishStatus,
            @Param("visibility") ContentVisibility visibility,
            Pageable pageable
    );

    @Query("""
            SELECT feedback
            FROM CustomerFeedback feedback, WorkCollection collection
            WHERE feedback.deleted = false
              AND feedback.collectionId = collection.id
              AND feedback.collectionId = :collectionId
              AND feedback.reviewStatus = :reviewStatus
              AND feedback.publishStatus = :feedbackPublishStatus
              AND collection.deleted = false
              AND collection.publishStatus = :collectionPublishStatus
              AND collection.visibility = :visibility
            ORDER BY feedback.publishedAt DESC, feedback.id DESC
            """)
    List<CustomerFeedback> findPublicFeedbackByCollection(
            @Param("collectionId") Long collectionId,
            @Param("reviewStatus") FeedbackReviewStatus reviewStatus,
            @Param("feedbackPublishStatus") FeedbackPublishStatus feedbackPublishStatus,
            @Param("collectionPublishStatus") PublishStatus collectionPublishStatus,
            @Param("visibility") ContentVisibility visibility
    );
}
