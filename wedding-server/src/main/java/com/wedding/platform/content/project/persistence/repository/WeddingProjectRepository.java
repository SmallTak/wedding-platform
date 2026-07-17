package com.wedding.platform.content.project.persistence.repository;

import com.wedding.platform.content.project.persistence.entity.WeddingProject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WeddingProjectRepository extends JpaRepository<WeddingProject, Long> {

    Optional<WeddingProject> findByIdAndDeletedFalse(Long id);

    boolean existsByProjectCode(String projectCode);

    @Query("""
            SELECT project
            FROM WeddingProject project
            WHERE project.deleted = false
              AND (
                :keyword IS NULL
                OR LOWER(project.projectCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(project.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(project.coupleDisplayName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(project.locationText) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
            ORDER BY project.createdAt DESC
            """)
    Page<WeddingProject> findAllProjects(@Param("keyword") String keyword, Pageable pageable);

    @Query("""
            SELECT project
            FROM WeddingProject project
            WHERE project.deleted = false
              AND (
                project.createdBy = :userId
                OR EXISTS (
                    SELECT creator.id.projectId
                    FROM ProjectCreator creator
                    WHERE creator.id.projectId = project.id
                      AND creator.id.creatorUserId = :userId
                )
              )
              AND (
                :keyword IS NULL
                OR LOWER(project.projectCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(project.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(project.coupleDisplayName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(project.locationText) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
            ORDER BY project.createdAt DESC
            """)
    Page<WeddingProject> findAccessibleProjects(
            @Param("userId") Long userId,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}
