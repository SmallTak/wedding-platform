package com.wedding.platform.content.project.persistence.entity;

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
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "wedding_project")
public class WeddingProject extends BaseBusinessEntity {

    @Column(name = "project_code", nullable = false, unique = true, length = 32)
    private String projectCode;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "couple_display_name", length = 100)
    private String coupleDisplayName;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "region_code", nullable = false, length = 64)
    private String regionCode;

    @Column(name = "location_text", nullable = false, length = 300)
    private String locationText;

    @Column(length = 5000)
    private String description;

    @Column(name = "cover_asset_id")
    private Long coverAssetId;

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
}
