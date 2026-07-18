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
              AND (:projectId IS NULL OR feedback.projectId = :projectId)
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
            @Param("projectId") Long projectId,
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
              AND (:projectId IS NULL OR feedback.projectId = :projectId)
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
            @Param("projectId") Long projectId,
            @Param("pendingStatus") FeedbackReviewStatus pendingStatus,
            @Param("rejectedStatus") FeedbackReviewStatus rejectedStatus,
            Pageable pageable
    );

    @Query("""
            SELECT feedback
            FROM CustomerFeedback feedback, WeddingProject project
            WHERE feedback.deleted = false
              AND feedback.projectId = project.id
              AND feedback.reviewStatus = :reviewStatus
              AND feedback.publishStatus = :feedbackPublishStatus
              AND project.deleted = false
              AND project.publishStatus = :projectPublishStatus
              AND project.visibility = :visibility
            ORDER BY feedback.publishedAt DESC, feedback.id DESC
            """)
    Page<CustomerFeedback> findPublicFeedback(
            @Param("reviewStatus") FeedbackReviewStatus reviewStatus,
            @Param("feedbackPublishStatus") FeedbackPublishStatus feedbackPublishStatus,
            @Param("projectPublishStatus") PublishStatus projectPublishStatus,
            @Param("visibility") ContentVisibility visibility,
            Pageable pageable
    );

    @Query("""
            SELECT feedback
            FROM CustomerFeedback feedback, WeddingProject project
            WHERE feedback.deleted = false
              AND feedback.projectId = project.id
              AND feedback.projectId = :projectId
              AND feedback.reviewStatus = :reviewStatus
              AND feedback.publishStatus = :feedbackPublishStatus
              AND project.deleted = false
              AND project.publishStatus = :projectPublishStatus
              AND project.visibility = :visibility
            ORDER BY feedback.publishedAt DESC, feedback.id DESC
            """)
    List<CustomerFeedback> findPublicFeedbackByProject(
            @Param("projectId") Long projectId,
            @Param("reviewStatus") FeedbackReviewStatus reviewStatus,
            @Param("feedbackPublishStatus") FeedbackPublishStatus feedbackPublishStatus,
            @Param("projectPublishStatus") PublishStatus projectPublishStatus,
            @Param("visibility") ContentVisibility visibility
    );
}
