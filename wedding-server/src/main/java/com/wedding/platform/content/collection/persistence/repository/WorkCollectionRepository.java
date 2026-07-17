package com.wedding.platform.content.collection.persistence.repository;

import com.wedding.platform.content.collection.persistence.entity.WorkCollection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WorkCollectionRepository extends JpaRepository<WorkCollection, Long> {

    Optional<WorkCollection> findByIdAndDeletedFalse(Long id);

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
}
