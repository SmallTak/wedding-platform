package com.wedding.platform.content.project.persistence.repository;

import com.wedding.platform.content.project.persistence.entity.ProjectCustomerApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectCustomerApplicationRepository
        extends JpaRepository<ProjectCustomerApplication, Long> {

    Optional<ProjectCustomerApplication> findByIdAndDeletedFalse(Long id);

    Optional<ProjectCustomerApplication> findByProjectIdAndCustomerUserIdAndDeletedFalse(
            Long projectId,
            Long customerUserId
    );

    List<ProjectCustomerApplication> findAllByCustomerUserIdAndDeletedFalseOrderByCreatedAtDesc(
            Long customerUserId
    );

    List<ProjectCustomerApplication> findAllByCustomerUserIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(
            Long customerUserId,
            String status
    );

    boolean existsByProjectIdAndCustomerUserIdAndStatusAndDeletedFalse(
            Long projectId,
            Long customerUserId,
            String status
    );

    @Query("""
            SELECT application
            FROM ProjectCustomerApplication application
            WHERE application.deleted = false
              AND (:status IS NULL OR application.status = :status)
            ORDER BY
              CASE WHEN application.status = 'PENDING' THEN 0 ELSE 1 END,
              application.createdAt DESC,
              application.id DESC
            """)
    Page<ProjectCustomerApplication> findAllForAdmin(
            @Param("status") String status,
            Pageable pageable
    );
}
