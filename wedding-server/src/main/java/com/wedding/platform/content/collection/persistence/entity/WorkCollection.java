package com.wedding.platform.content.collection.persistence.entity;

import com.wedding.platform.content.shared.ContentVisibility;
import com.wedding.platform.content.shared.PublishStatus;
import com.wedding.platform.content.shared.ReviewStatus;
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
@Table(name = "work_collection")
public class WorkCollection extends BaseBusinessEntity {

    @Column(name = "project_id")
    private Long projectId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 5000)
    private String description;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "cover_photo_id")
    private Long coverPhotoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ContentVisibility visibility;

    @Column(name = "access_password_hash")
    private String accessPasswordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 32)
    private ReviewStatus reviewStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "publish_status", nullable = false, length = 32)
    private PublishStatus publishStatus;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_by")
    private Long publishedBy;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "offline_reason", length = 500)
    private String offlineReason;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "is_featured", nullable = false)
    private Boolean featured;

    @Column(name = "is_pinned", nullable = false)
    private Boolean pinned;
}
