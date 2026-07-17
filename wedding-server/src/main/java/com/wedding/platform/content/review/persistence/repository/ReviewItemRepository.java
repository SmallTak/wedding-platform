package com.wedding.platform.content.review.persistence.repository;

import com.wedding.platform.content.review.persistence.entity.ReviewItem;
import com.wedding.platform.content.review.persistence.entity.ReviewItemType;
import com.wedding.platform.content.review.persistence.entity.ReviewTargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewItemRepository extends JpaRepository<ReviewItem, Long> {

    List<ReviewItem> findAllByTaskIdOrderByIdAsc(Long taskId);

    List<ReviewItem> findAllByTaskIdInOrderByTaskIdDescIdAsc(List<Long> taskIds);

    @Query("""
            SELECT item
            FROM ReviewItem item
            JOIN ReviewTask task ON task.id = item.taskId
            WHERE task.targetType = :targetType
              AND task.targetId = :targetId
              AND item.current = true
            ORDER BY item.itemType, item.fieldKey, item.businessId
            """)
    List<ReviewItem> findCurrentItems(
            @Param("targetType") ReviewTargetType targetType,
            @Param("targetId") Long targetId
    );

    @Query("""
            SELECT item
            FROM ReviewItem item
            JOIN ReviewTask task ON task.id = item.taskId
            WHERE task.targetType = :targetType
              AND task.targetId = :targetId
              AND item.itemType = :itemType
              AND item.current = true
            ORDER BY item.fieldKey, item.businessId
            """)
    List<ReviewItem> findCurrentItemsByType(
            @Param("targetType") ReviewTargetType targetType,
            @Param("targetId") Long targetId,
            @Param("itemType") ReviewItemType itemType
    );

    @Query("""
            SELECT item
            FROM ReviewItem item
            JOIN ReviewTask task ON task.id = item.taskId
            WHERE task.targetType = :targetType
              AND task.targetId = :targetId
              AND item.itemType = :itemType
              AND item.fieldKey = :fieldKey
              AND item.current = true
            """)
    Optional<ReviewItem> findCurrentField(
            @Param("targetType") ReviewTargetType targetType,
            @Param("targetId") Long targetId,
            @Param("itemType") ReviewItemType itemType,
            @Param("fieldKey") String fieldKey
    );

    @Query("""
            SELECT item
            FROM ReviewItem item
            JOIN ReviewTask task ON task.id = item.taskId
            WHERE task.targetType = :targetType
              AND task.targetId = :targetId
              AND item.itemType = :itemType
              AND item.businessId = :businessId
              AND item.current = true
            """)
    Optional<ReviewItem> findCurrentBusinessItem(
            @Param("targetType") ReviewTargetType targetType,
            @Param("targetId") Long targetId,
            @Param("itemType") ReviewItemType itemType,
            @Param("businessId") Long businessId
    );

    @Query("""
            SELECT item
            FROM ReviewItem item
            JOIN ReviewTask task ON task.id = item.taskId
            WHERE task.targetType = :targetType
              AND task.targetId = :targetId
              AND item.itemType = :itemType
              AND (
                (:fieldKey IS NOT NULL AND item.fieldKey = :fieldKey)
                OR (:businessId IS NOT NULL AND item.businessId = :businessId)
              )
              AND item.id <> :excludedId
            ORDER BY item.revisionNo DESC, item.id DESC
            """)
    List<ReviewItem> findPreviousItems(
            @Param("targetType") ReviewTargetType targetType,
            @Param("targetId") Long targetId,
            @Param("itemType") ReviewItemType itemType,
            @Param("fieldKey") String fieldKey,
            @Param("businessId") Long businessId,
            @Param("excludedId") Long excludedId
    );
}
