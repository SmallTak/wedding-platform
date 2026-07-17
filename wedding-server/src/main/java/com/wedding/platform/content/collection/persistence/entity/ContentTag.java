package com.wedding.platform.content.collection.persistence.entity;

import com.wedding.platform.platform.persistence.BaseBusinessEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "content_tag")
public class ContentTag extends BaseBusinessEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(nullable = false, length = 32)
    private String status;
}
