package com.wedding.platform.content.media.persistence.repository;

import com.wedding.platform.content.media.persistence.entity.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {
}
