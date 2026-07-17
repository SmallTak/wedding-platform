package com.wedding.platform.system.account.persistence.repository;

import com.wedding.platform.system.account.persistence.entity.ProfessionalRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProfessionalRoleRepository extends JpaRepository<ProfessionalRole, Long> {

    List<ProfessionalRole> findAllByStatusAndDeletedFalseOrderBySortOrderAsc(String status);
}
