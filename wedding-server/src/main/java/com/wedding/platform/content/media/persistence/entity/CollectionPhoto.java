package com.wedding.platform.content.media.persistence.entity;

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
@Table(name = "collection_photo")
public class CollectionPhoto extends BaseBusinessEntity {

    @Column(name = "collection_id", nullable = false)
    private Long collectionId;

    @Column(name = "asset_id", nullable = false, unique = true)
    private Long assetId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 32)
    private ReviewStatus reviewStatus;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;
}
