package com.wedding.platform.content.project.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "project_creator")
public class ProjectCreator {

    @EmbeddedId
    private ProjectCreatorId id;

    @Column(name = "assigned_by", nullable = false)
    private Long assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @PrePersist
    void prePersist() {
        assignedAt = assignedAt == null ? Instant.now() : assignedAt;
    }
}
