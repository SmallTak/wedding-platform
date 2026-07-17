package com.wedding.platform.system.account.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "sys_user")
public class SystemUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String mobile;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "avatar_path", length = 500)
    private String avatarPath;

    @Column(name = "account_type", nullable = false, length = 32)
    private String accountType;

    @Column(name = "account_status", nullable = false, length = 32)
    private String accountStatus;

    @Column(name = "must_change_password", nullable = false)
    private Boolean mustChangePassword;

    @Column(name = "profile_completed", nullable = false)
    private Boolean profileCompleted;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "is_deleted", nullable = false)
    private Boolean deleted;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "system_user_role",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<SystemRole> roles = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "creator_professional_role",
            joinColumns = @JoinColumn(name = "creator_user_id"),
            inverseJoinColumns = @JoinColumn(name = "professional_role_id")
    )
    private Set<ProfessionalRole> professionalRoles = new LinkedHashSet<>();

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
