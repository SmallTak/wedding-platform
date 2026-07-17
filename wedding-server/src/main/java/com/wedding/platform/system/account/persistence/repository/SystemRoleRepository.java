package com.wedding.platform.system.account.persistence.repository;

import com.wedding.platform.system.account.persistence.entity.SystemRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SystemRoleRepository extends JpaRepository<SystemRole, Long> {

    Optional<SystemRole> findByCodeAndStatus(String code, String status);
}
