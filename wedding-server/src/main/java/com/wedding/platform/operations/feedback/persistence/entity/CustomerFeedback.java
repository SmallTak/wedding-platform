package com.wedding.platform.operations.feedback.persistence.entity;

import com.wedding.platform.platform.persistence.BaseBusinessEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "customer_feedback")
public class CustomerFeedback extends BaseBusinessEntity {

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "creator_user_id", nullable = false)
    private Long creatorUserId;

    @Column(name = "customer_user_id")
    private Long customerUserId;

    @Column(name = "customer_display_name", nullable = false, length = 100)
    private String customerDisplayName;

    @Column(name = "submitted_by", nullable = false)
    private Long submittedBy;

    @Column(nullable = false)
    private Integer rating;

    @Column(nullable = false, length = 2000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 32)
    private FeedbackReviewStatus reviewStatus;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "publish_status", nullable = false, length = 32)
    private FeedbackPublishStatus publishStatus;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "offline_reason", length = 500)
    private String offlineReason;
}
