package com.wedding.platform.content.media.persistence.repository;

import com.wedding.platform.content.media.persistence.entity.CollectionPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CollectionPhotoRepository extends JpaRepository<CollectionPhoto, Long> {

    List<CollectionPhoto> findAllByCollectionIdAndDeletedFalseOrderBySortOrderAscIdAsc(Long collectionId);

    Optional<CollectionPhoto> findByIdAndCollectionIdAndDeletedFalse(Long id, Long collectionId);

    @Query("""
            SELECT COALESCE(MAX(photo.sortOrder), -1)
            FROM CollectionPhoto photo
            WHERE photo.collectionId = :collectionId
              AND photo.deleted = false
            """)
    int findMaxSortOrder(@Param("collectionId") Long collectionId);
}
