package com.wedding.platform.content.media.persistence.repository;

import com.wedding.platform.content.media.persistence.entity.CollectionPhoto;
import com.wedding.platform.content.shared.ContentVisibility;
import com.wedding.platform.content.shared.PublishStatus;
import com.wedding.platform.content.shared.ReviewStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
            SELECT photo
            FROM CollectionPhoto photo, WorkCollection collection
            WHERE photo.collectionId = collection.id
              AND photo.deleted = false
              AND photo.reviewStatus = :reviewStatus
              AND collection.deleted = false
              AND collection.publishStatus = :publishStatus
              AND collection.visibility = :visibility
            ORDER BY collection.publishedAt DESC, collection.id DESC, photo.sortOrder ASC, photo.id ASC
            """)
    List<CollectionPhoto> findHomepageCarouselCandidates(
            @Param("reviewStatus") ReviewStatus reviewStatus,
            @Param("publishStatus") PublishStatus publishStatus,
            @Param("visibility") ContentVisibility visibility,
            Pageable pageable
    );

    @Query("""
            SELECT COALESCE(MAX(photo.sortOrder), -1)
            FROM CollectionPhoto photo
            WHERE photo.collectionId = :collectionId
              AND photo.deleted = false
            """)
    int findMaxSortOrder(@Param("collectionId") Long collectionId);
}
