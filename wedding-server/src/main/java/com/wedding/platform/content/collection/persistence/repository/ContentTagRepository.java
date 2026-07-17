package com.wedding.platform.content.collection.persistence.repository;

import com.wedding.platform.content.collection.persistence.entity.ContentTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContentTagRepository extends JpaRepository<ContentTag, Long> {

    Optional<ContentTag> findByIdAndDeletedFalse(Long id);

    List<ContentTag> findAllByDeletedFalseOrderBySortOrderAscCreatedAtAsc();

    List<ContentTag> findAllByStatusAndDeletedFalseOrderBySortOrderAscCreatedAtAsc(String status);

    boolean existsByNameIgnoreCaseAndDeletedFalse(String name);

    boolean existsByNameIgnoreCaseAndDeletedFalseAndIdNot(String name, Long id);
}
