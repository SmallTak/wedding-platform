package com.wedding.platform.content.review.persistence.repository;

import com.wedding.platform.content.review.persistence.entity.ReviewActionLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewActionLogRepository extends JpaRepository<ReviewActionLog, Long> {
}
