package com.wedding.platform.system.account.persistence.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PermissionRepository extends Repository<com.wedding.platform.system.account.persistence.entity.SystemRole, Long> {

    @Query(value = """
            SELECT DISTINCT permission.resource
            FROM system_permission permission
            JOIN system_role_permission relation ON relation.permission_id = permission.id
            JOIN system_user_role user_role ON user_role.role_id = relation.role_id
            WHERE user_role.user_id = :userId
              AND permission.status = 'ACTIVE'
            ORDER BY permission.resource
            """, nativeQuery = true)
    List<String> findResourcesByUserId(@Param("userId") Long userId);
}
