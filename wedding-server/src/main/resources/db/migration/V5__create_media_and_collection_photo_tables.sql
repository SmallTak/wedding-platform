CREATE TABLE media_asset (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    original_name VARCHAR(255) NOT NULL,
    storage_name VARCHAR(100) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    width INT NOT NULL,
    height INT NOT NULL,
    original_path VARCHAR(500) NOT NULL,
    preview_path VARCHAR(500) NOT NULL,
    thumbnail_path VARCHAR(500) NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    process_status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS',
    failure_reason VARCHAR(500),
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT uk_media_asset_storage_name UNIQUE (storage_name),
    CONSTRAINT fk_media_asset_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id),
    CONSTRAINT fk_media_asset_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id)
);

CREATE TABLE collection_photo (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    collection_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    review_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    rejection_reason VARCHAR(500),
    submitted_at TIMESTAMP NULL,
    reviewed_at TIMESTAMP NULL,
    reviewed_by BIGINT,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT uk_collection_photo_asset UNIQUE (asset_id),
    CONSTRAINT fk_collection_photo_collection FOREIGN KEY (collection_id) REFERENCES work_collection (id),
    CONSTRAINT fk_collection_photo_asset FOREIGN KEY (asset_id) REFERENCES media_asset (id),
    CONSTRAINT fk_collection_photo_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES sys_user (id),
    CONSTRAINT fk_collection_photo_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id),
    CONSTRAINT fk_collection_photo_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id)
);

ALTER TABLE work_collection
    ADD CONSTRAINT fk_work_collection_cover_photo
    FOREIGN KEY (cover_photo_id) REFERENCES collection_photo (id);

CREATE INDEX idx_media_asset_checksum ON media_asset (checksum, is_deleted);
CREATE INDEX idx_media_asset_process_status ON media_asset (process_status, created_at);
CREATE INDEX idx_collection_photo_order ON collection_photo (collection_id, is_deleted, sort_order);
CREATE INDEX idx_collection_photo_review ON collection_photo (review_status, submitted_at);
