package com.wedding.platform.content.collection.persistence.repository;

import com.wedding.platform.content.collection.persistence.entity.CollectionTag;
import com.wedding.platform.content.collection.persistence.entity.CollectionTagId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CollectionTagRepository extends JpaRepository<CollectionTag, CollectionTagId> {

    @Query("""
            SELECT relation
            FROM CollectionTag relation
            WHERE relation.id.collectionId = :collectionId
            ORDER BY relation.createdAt ASC
            """)
    List<CollectionTag> findAllByCollectionId(@Param("collectionId") Long collectionId);
}
