package com.wedding.platform.operations.site.persistence.entity;

import com.wedding.platform.platform.persistence.BaseBusinessEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "homepage_carousel_item",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_homepage_carousel_photo",
                columnNames = "photo_id"
        )
)
public class HomepageCarouselItem extends BaseBusinessEntity {

    @Column(name = "photo_id", nullable = false)
    private Long photoId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "focal_x", nullable = false, precision = 5, scale = 2)
    private BigDecimal focalX;

    @Column(name = "focal_y", nullable = false, precision = 5, scale = 2)
    private BigDecimal focalY;

    @Column(nullable = false, length = 32)
    private String status;
}
