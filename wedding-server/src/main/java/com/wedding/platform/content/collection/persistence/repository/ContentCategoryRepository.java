package com.wedding.platform.content.collection.persistence.repository;

import com.wedding.platform.content.collection.persistence.entity.ContentCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContentCategoryRepository extends JpaRepository<ContentCategory, Long> {

    Optional<ContentCategory> findByIdAndDeletedFalse(Long id);

    List<ContentCategory> findAllByDeletedFalseOrderBySortOrderAscCreatedAtAsc();

    List<ContentCategory> findAllByStatusAndDeletedFalseOrderBySortOrderAscCreatedAtAsc(String status);

    boolean existsByNameIgnoreCaseAndDeletedFalse(String name);

    boolean existsByNameIgnoreCaseAndDeletedFalseAndIdNot(String name, Long id);
}
