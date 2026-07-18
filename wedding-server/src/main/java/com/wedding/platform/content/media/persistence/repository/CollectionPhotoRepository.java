package com.wedding.platform.content.media.persistence.repository;

import com.wedding.platform.content.media.persistence.entity.CollectionPhoto;
import com.wedding.platform.content.shared.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CollectionPhotoRepository extends JpaRepository<CollectionPhoto, Long> {

    List<CollectionPhoto> findAllByCollectionIdAndDeletedFalseOrderBySortOrderAscIdAsc(Long collectionId);

    Optional<CollectionPhoto> findByIdAndCollectionIdAndDeletedFalse(Long id, Long collectionId);

    List<CollectionPhoto> findAllByCollectionIdAndDeletedFalseAndReviewStatusOrderBySortOrderAscIdAsc(
            Long collectionId,
            ReviewStatus reviewStatus
    );

    @Query("""
            SELECT COALESCE(MAX(photo.sortOrder), -1)
            FROM CollectionPhoto photo
            WHERE photo.collectionId = :collectionId
              AND photo.deleted = false
            """)
    int findMaxSortOrder(@Param("collectionId") Long collectionId);

    @Query("""
            SELECT photo
            FROM CollectionPhoto photo, SystemUser user
            WHERE photo.createdBy = user.id
              AND user.accountType = :accountType
              AND photo.deleted = false
              AND photo.createdAt >= :createdAt
              AND photo.createdAt < :endAt
            ORDER BY photo.createdAt
            """)
    List<CollectionPhoto> findCreatorUploadsBetween(
            @Param("createdAt") Instant createdAt,
            @Param("endAt") Instant endAt,
            @Param("accountType") String accountType
    );
}
