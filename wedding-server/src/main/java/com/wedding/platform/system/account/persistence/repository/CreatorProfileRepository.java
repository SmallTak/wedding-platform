package com.wedding.platform.system.account.persistence.repository;

import com.wedding.platform.system.account.persistence.entity.CreatorProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreatorProfileRepository extends JpaRepository<CreatorProfile, Long> {
}
