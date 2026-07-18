package com.wedding.platform.operations.site.persistence.entity;

import com.wedding.platform.platform.persistence.BaseBusinessEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "homepage_feature",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_homepage_feature_target",
                columnNames = {"target_type", "target_id"}
        )
)
public class HomepageFeature extends BaseBusinessEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 32)
    private HomepageFeatureTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "is_pinned", nullable = false)
    private Boolean pinned;

    @Column(nullable = false, length = 32)
    private String status;
}
