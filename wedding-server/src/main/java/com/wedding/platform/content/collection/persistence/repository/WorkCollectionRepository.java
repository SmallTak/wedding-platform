package com.wedding.platform.content.collection.persistence.repository;

import com.wedding.platform.content.collection.persistence.entity.WorkCollection;
import com.wedding.platform.content.shared.ContentVisibility;
import com.wedding.platform.content.shared.PublishStatus;
import com.wedding.platform.content.shared.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkCollectionRepository extends JpaRepository<WorkCollection, Long> {

    Optional<WorkCollection> findByIdAndDeletedFalse(Long id);

    boolean existsByProjectIdAndDeletedFalse(Long projectId);

    boolean existsByCategoryIdAndDeletedFalse(Long categoryId);

    @Query("""
            SELECT work
            FROM WorkCollection work
            WHERE work.deleted = false
              AND (:projectId IS NULL OR work.projectId = :projectId)
              AND (:categoryId IS NULL OR work.categoryId = :categoryId)
              AND (
                :keyword IS NULL
                OR LOWER(work.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(work.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
            ORDER BY work.createdAt DESC
            """)
    Page<WorkCollection> findAllCollections(
            @Param("projectId") Long projectId,
            @Param("categoryId") Long categoryId,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("""
            SELECT work
            FROM WorkCollection work
            WHERE work.deleted = false
              AND (
                work.createdBy = :userId
                OR EXISTS (
                    SELECT creator.id.collectionId
                    FROM CollectionCreator creator
                    WHERE creator.id.collectionId = work.id
                      AND creator.id.creatorUserId = :userId
                )
              )
              AND (:projectId IS NULL OR work.projectId = :projectId)
              AND (:categoryId IS NULL OR work.categoryId = :categoryId)
              AND (
                :keyword IS NULL
                OR LOWER(work.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(work.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
            ORDER BY work.createdAt DESC
            """)
    Page<WorkCollection> findAccessibleCollections(
            @Param("userId") Long userId,
            @Param("projectId") Long projectId,
            @Param("categoryId") Long categoryId,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("""
            SELECT work
            FROM WorkCollection work
            WHERE work.deleted = false
              AND (:reviewStatus IS NULL OR work.reviewStatus = :reviewStatus)
              AND (:publishStatus IS NULL OR work.publishStatus = :publishStatus)
              AND (
                :keyword IS NULL
                OR LOWER(work.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(work.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
            ORDER BY
              CASE WHEN work.reviewStatus = :pendingStatus THEN 0
                   WHEN work.reviewStatus = :rejectedStatus THEN 1
                   WHEN work.publishStatus = :readyStatus THEN 2
                   ELSE 3 END,
              work.submittedAt DESC,
              work.updatedAt DESC
            """)
    Page<WorkCollection> findWorkflowCollections(
            @Param("reviewStatus") ReviewStatus reviewStatus,
            @Param("publishStatus") PublishStatus publishStatus,
            @Param("keyword") String keyword,
            @Param("pendingStatus") ReviewStatus pendingStatus,
            @Param("rejectedStatus") ReviewStatus rejectedStatus,
            @Param("readyStatus") PublishStatus readyStatus,
            Pageable pageable
    );

    @Query("""
            SELECT work
            FROM WorkCollection work
            WHERE work.deleted = false
              AND work.publishStatus = :publishStatus
              AND work.visibility = :visibility
              AND (:categoryId IS NULL OR work.categoryId = :categoryId)
              AND (
                :keyword IS NULL
                OR LOWER(work.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(work.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
            ORDER BY work.pinned DESC, work.sortOrder ASC, work.publishedAt DESC, work.id DESC
            """)
    Page<WorkCollection> findPublicCollections(
            @Param("publishStatus") PublishStatus publishStatus,
            @Param("visibility") ContentVisibility visibility,
            @Param("categoryId") Long categoryId,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    Optional<WorkCollection> findByIdAndDeletedFalseAndPublishStatusAndVisibility(
            Long id,
            PublishStatus publishStatus,
            ContentVisibility visibility
    );

    Optional<WorkCollection> findByIdAndDeletedFalseAndPublishStatus(
            Long id,
            PublishStatus publishStatus
    );

    @Query("""
            SELECT work
            FROM WorkCollection work
            WHERE work.deleted = false
              AND work.publishStatus = :publishStatus
              AND work.visibility = :visibility
            ORDER BY work.publishedAt DESC, work.id DESC
            """)
    List<WorkCollection> findLatestPublicCollections(
            @Param("publishStatus") PublishStatus publishStatus,
            @Param("visibility") ContentVisibility visibility,
            Pageable pageable
    );

    @Query("""
            SELECT work
            FROM WorkCollection work
            WHERE work.deleted = false
              AND work.reviewStatus = :reviewStatus
              AND work.publishStatus = :publishStatus
              AND work.visibility = :visibility
              AND work.coverPhotoId IS NOT NULL
            ORDER BY work.publishedAt DESC, work.id DESC
            """)
    List<WorkCollection> findHomepageCarouselCandidates(
            @Param("reviewStatus") ReviewStatus reviewStatus,
            @Param("publishStatus") PublishStatus publishStatus,
            @Param("visibility") ContentVisibility visibility,
            Pageable pageable
    );

    @Query("""
            SELECT work
            FROM WorkCollection work
            WHERE work.deleted = false
              AND work.projectId = :projectId
              AND work.publishStatus = :publishStatus
              AND work.visibility = :visibility
            ORDER BY work.pinned DESC, work.sortOrder ASC, work.publishedAt DESC, work.id DESC
            """)
    List<WorkCollection> findPublishedCollectionsByProject(
            @Param("projectId") Long projectId,
            @Param("publishStatus") PublishStatus publishStatus,
            @Param("visibility") ContentVisibility visibility
    );

    List<WorkCollection> findTop5ByDeletedFalseAndReviewStatusInOrderBySubmittedAtDescUpdatedAtDesc(
            List<ReviewStatus> reviewStatuses
    );

    long countByDeletedFalseAndReviewStatus(ReviewStatus reviewStatus);

    long countByDeletedFalseAndPublishStatus(PublishStatus publishStatus);
}
