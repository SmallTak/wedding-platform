package com.wedding.platform.operations.site.persistence.repository;

import com.wedding.platform.operations.site.persistence.entity.HomepageFeature;
import com.wedding.platform.operations.site.persistence.entity.HomepageFeatureTargetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HomepageFeatureRepository extends JpaRepository<HomepageFeature, Long> {

    List<HomepageFeature> findAllByDeletedFalseOrderByTargetTypeAscPinnedDescSortOrderAscIdAsc();

    List<HomepageFeature> findAllByTargetTypeAndStatusAndDeletedFalseOrderByPinnedDescSortOrderAscIdAsc(
            HomepageFeatureTargetType targetType,
            String status
    );
}
