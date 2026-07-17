package com.wedding.platform.content.project.persistence.repository;

import com.wedding.platform.content.project.persistence.entity.ProjectCreator;
import com.wedding.platform.content.project.persistence.entity.ProjectCreatorId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectCreatorRepository extends JpaRepository<ProjectCreator, ProjectCreatorId> {

    @Query("""
            SELECT creator
            FROM ProjectCreator creator
            WHERE creator.id.projectId = :projectId
            ORDER BY creator.assignedAt ASC
            """)
    List<ProjectCreator> findAllByProjectId(@Param("projectId") Long projectId);

    @Query("""
            SELECT CASE WHEN COUNT(creator) > 0 THEN true ELSE false END
            FROM ProjectCreator creator
            WHERE creator.id.projectId = :projectId
              AND creator.id.creatorUserId = :creatorUserId
            """)
    boolean existsByProjectIdAndCreatorUserId(
            @Param("projectId") Long projectId,
            @Param("creatorUserId") Long creatorUserId
    );
}
