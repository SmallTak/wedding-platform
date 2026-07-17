package com.wedding.platform.content.media.persistence.entity;

import com.wedding.platform.platform.persistence.BaseBusinessEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "media_asset")
public class MediaAsset extends BaseBusinessEntity {

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "storage_name", nullable = false, unique = true, length = 100)
    private String storageName;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(nullable = false)
    private Integer width;

    @Column(nullable = false)
    private Integer height;

    @Column(name = "original_path", nullable = false, length = 500)
    private String originalPath;

    @Column(name = "preview_path", nullable = false, length = 500)
    private String previewPath;

    @Column(name = "thumbnail_path", nullable = false, length = 500)
    private String thumbnailPath;

    @Column(nullable = false, length = 64)
    private String checksum;

    @Column(name = "process_status", nullable = false, length = 32)
    private String processStatus;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;
}
