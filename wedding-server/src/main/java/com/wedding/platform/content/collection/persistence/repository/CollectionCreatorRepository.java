package com.wedding.platform.content.collection.persistence.repository;

import com.wedding.platform.content.collection.persistence.entity.CollectionCreator;
import com.wedding.platform.content.collection.persistence.entity.CollectionCreatorId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CollectionCreatorRepository extends JpaRepository<CollectionCreator, CollectionCreatorId> {

    @Query("""
            SELECT creator
            FROM CollectionCreator creator
            WHERE creator.id.collectionId = :collectionId
            ORDER BY creator.joinedAt ASC
            """)
    List<CollectionCreator> findAllByCollectionId(@Param("collectionId") Long collectionId);

    @Query("""
            SELECT CASE WHEN COUNT(creator) > 0 THEN true ELSE false END
            FROM CollectionCreator creator
            WHERE creator.id.collectionId = :collectionId
              AND creator.id.creatorUserId = :creatorUserId
            """)
    boolean existsByCollectionIdAndCreatorUserId(
            @Param("collectionId") Long collectionId,
            @Param("creatorUserId") Long creatorUserId
    );
}
