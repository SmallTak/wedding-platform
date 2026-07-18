package com.wedding.platform.operations.site.persistence.repository;

import com.wedding.platform.operations.site.persistence.entity.HomepageCarouselItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HomepageCarouselItemRepository extends JpaRepository<HomepageCarouselItem, Long> {

    List<HomepageCarouselItem> findAllByStatusAndDeletedFalseOrderBySortOrderAscIdAsc(String status);
}
