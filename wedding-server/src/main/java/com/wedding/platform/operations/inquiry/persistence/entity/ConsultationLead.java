package com.wedding.platform.operations.inquiry.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "consultation_lead")
public class ConsultationLead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference_code", nullable = false, unique = true, length = 32)
    private String referenceCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 120)
    private String contact;

    @Column(name = "wedding_date")
    private LocalDate weddingDate;

    @Column(length = 200)
    private String region;

    @Column(name = "service_needs", nullable = false, length = 1000)
    private String serviceNeeds;

    @Column(length = 2000)
    private String remark;

    @Enumerated(EnumType.STRING)
    @Column(name = "follow_status", nullable = false, length = 32)
    private InquiryFollowStatus followStatus;

    @Column(name = "follow_note", length = 2000)
    private String followNote;

    @Column(name = "assigned_admin_id")
    private Long assignedAdminId;

    @Column(nullable = false, length = 32)
    private String source;

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
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        version = version == null ? 0L : version;
        deleted = deleted == null ? Boolean.FALSE : deleted;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
