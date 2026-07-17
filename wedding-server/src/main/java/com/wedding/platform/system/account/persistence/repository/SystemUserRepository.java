package com.wedding.platform.system.account.persistence.repository;

import com.wedding.platform.system.account.persistence.entity.SystemUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SystemUserRepository extends JpaRepository<SystemUser, Long> {

    Optional<SystemUser> findByMobileAndDeletedFalse(String mobile);

    boolean existsByMobile(String mobile);

    boolean existsByAccountTypeAndDeletedFalse(String accountType);

    List<SystemUser> findAllByAccountTypeAndDeletedFalseOrderByCreatedAtDesc(String accountType);
}
