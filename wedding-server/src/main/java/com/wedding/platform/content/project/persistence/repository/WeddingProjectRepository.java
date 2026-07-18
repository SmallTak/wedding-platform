package com.wedding.platform.content.project.persistence.repository;

import com.wedding.platform.content.project.persistence.entity.WeddingProject;
import com.wedding.platform.content.shared.ContentVisibility;
import com.wedding.platform.content.shared.PublishStatus;
import com.wedding.platform.content.shared.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface WeddingProjectRepository extends JpaRepository<WeddingProject, Long> {

    Optional<WeddingProject> findByIdAndDeletedFalse(Long id);

    Optional<WeddingProject> findByProjectCodeAndDeletedFalse(String projectCode);

    Optional<WeddingProject> findByIdAndDeletedFalseAndPublishStatus(Long id, PublishStatus publishStatus);

    @Query("""
            SELECT project
            FROM WeddingProject project
            WHERE project.deleted = false
              AND project.publishStatus = :publishStatus
              AND project.visibility = :visibility
            ORDER BY project.publishedAt DESC, project.id DESC
            """)
    List<WeddingProject> findLatestPublicProjects(
            @Param("publishStatus") PublishStatus publishStatus,
            @Param("visibility") ContentVisibility visibility,
            Pageable pageable
    );

    boolean existsByProjectCode(String projectCode);

    List<WeddingProject> findAllByDeletedFalseOrderByCreatedAtDesc();

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
            ORDER BY project.createdAt DESC
            """)
    List<WeddingProject> findAllAccessibleProjects(@Param("userId") Long userId);

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

    @Query("""
            SELECT project
            FROM WeddingProject project
            WHERE project.deleted = false
              AND project.publishStatus = :publishStatus
              AND project.visibility = :visibility
              AND (:regionCode IS NULL OR project.regionCode = :regionCode)
              AND (
                :keyword IS NULL
                OR LOWER(project.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(project.coupleDisplayName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(project.locationText) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(project.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
            ORDER BY project.publishedAt DESC, project.id DESC
            """)
    Page<WeddingProject> findPublicProjects(
            @Param("publishStatus") PublishStatus publishStatus,
            @Param("visibility") ContentVisibility visibility,
            @Param("regionCode") String regionCode,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("""
            SELECT project
            FROM WeddingProject project
            WHERE project.deleted = false
              AND (:reviewStatus IS NULL OR project.reviewStatus = :reviewStatus)
              AND (:publishStatus IS NULL OR project.publishStatus = :publishStatus)
              AND (
                :keyword IS NULL
                OR LOWER(project.projectCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(project.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(project.coupleDisplayName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(project.locationText) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
            ORDER BY
              CASE WHEN project.reviewStatus = :pendingStatus THEN 0
                   WHEN project.reviewStatus = :rejectedStatus THEN 1
                   WHEN project.publishStatus = :readyStatus THEN 2
                   ELSE 3 END,
              project.submittedAt DESC,
              project.updatedAt DESC
            """)
    Page<WeddingProject> findWorkflowProjects(
            @Param("reviewStatus") ReviewStatus reviewStatus,
            @Param("publishStatus") PublishStatus publishStatus,
            @Param("keyword") String keyword,
            @Param("pendingStatus") ReviewStatus pendingStatus,
            @Param("rejectedStatus") ReviewStatus rejectedStatus,
            @Param("readyStatus") PublishStatus readyStatus,
            Pageable pageable
    );

    long countByDeletedFalseAndReviewStatus(ReviewStatus reviewStatus);

    long countByDeletedFalseAndPublishStatus(PublishStatus publishStatus);
}
