package com.wedding.platform.content.project.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.wedding.platform.platform.persistence.BaseBusinessEntity;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "project_customer_application",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_project_customer_application",
                columnNames = {"project_id", "customer_user_id"}
        )
)
public class ProjectCustomerApplication extends BaseBusinessEntity {

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "customer_user_id", nullable = false)
    private Long customerUserId;

    @Column(name = "apply_note", nullable = false, length = 1000)
    private String applyNote;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private java.time.Instant reviewedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;
}
