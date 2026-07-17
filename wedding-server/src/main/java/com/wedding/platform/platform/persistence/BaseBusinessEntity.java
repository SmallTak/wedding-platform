package com.wedding.platform.platform.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@MappedSuperclass
public abstract class BaseBusinessEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "is_deleted", nullable = false)
    private Boolean deleted;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    protected void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        version = version == null ? 0L : version;
        deleted = deleted == null ? Boolean.FALSE : deleted;
    }

    @PreUpdate
    protected void preUpdate() {
        updatedAt = Instant.now();
    }
}
