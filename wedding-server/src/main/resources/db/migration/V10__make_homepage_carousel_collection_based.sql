DROP TABLE homepage_carousel_item;

CREATE TABLE homepage_carousel_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    collection_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    focal_x DECIMAL(5, 2) NOT NULL DEFAULT 50.00,
    focal_y DECIMAL(5, 2) NOT NULL DEFAULT 50.00,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT uk_homepage_carousel_collection UNIQUE (collection_id),
    CONSTRAINT fk_homepage_carousel_collection FOREIGN KEY (collection_id) REFERENCES work_collection (id),
    CONSTRAINT fk_homepage_carousel_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id),
    CONSTRAINT fk_homepage_carousel_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id)
);

CREATE INDEX idx_homepage_carousel_order
    ON homepage_carousel_item (status, is_deleted, sort_order, id);

UPDATE homepage_feature
SET status = 'INACTIVE',
    is_deleted = TRUE,
    deleted_at = CURRENT_TIMESTAMP
WHERE target_type = 'COLLECTION'
  AND is_deleted = FALSE;
