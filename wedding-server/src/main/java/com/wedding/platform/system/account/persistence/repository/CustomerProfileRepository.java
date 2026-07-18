package com.wedding.platform.system.account.persistence.repository;

import com.wedding.platform.system.account.persistence.entity.CustomerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, Long> {
}
