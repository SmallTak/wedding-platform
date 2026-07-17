package com.wedding.platform.content.review.persistence.repository;

import com.wedding.platform.content.review.persistence.entity.ReviewTargetType;
import com.wedding.platform.content.review.persistence.entity.ReviewTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewTaskRepository extends JpaRepository<ReviewTask, Long> {

    List<ReviewTask> findAllByTargetTypeAndTargetIdOrderByRevisionNoDesc(
            ReviewTargetType targetType,
            Long targetId
    );

    Optional<ReviewTask> findTopByTargetTypeAndTargetIdOrderByRevisionNoDesc(
            ReviewTargetType targetType,
            Long targetId
    );

    @Query("""
            SELECT COALESCE(MAX(task.revisionNo), 0)
            FROM ReviewTask task
            WHERE task.targetType = :targetType
              AND task.targetId = :targetId
            """)
    int findMaxRevisionNo(
            @Param("targetType") ReviewTargetType targetType,
            @Param("targetId") Long targetId
    );
}
