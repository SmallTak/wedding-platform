CREATE TABLE wedding_project (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_code VARCHAR(32) NOT NULL,
    title VARCHAR(200) NOT NULL,
    couple_display_name VARCHAR(100),
    event_date DATE NOT NULL,
    region_code VARCHAR(64) NOT NULL,
    location_text VARCHAR(300) NOT NULL,
    description VARCHAR(5000),
    cover_asset_id BIGINT,
    visibility VARCHAR(32) NOT NULL DEFAULT 'HIDDEN',
    access_password_hash VARCHAR(255),
    review_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    publish_status VARCHAR(32) NOT NULL DEFAULT 'UNPUBLISHED',
    published_at TIMESTAMP NULL,
    published_by BIGINT,
    offline_reason VARCHAR(500),
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT uk_wedding_project_code UNIQUE (project_code),
    CONSTRAINT fk_wedding_project_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id),
    CONSTRAINT fk_wedding_project_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id),
    CONSTRAINT fk_wedding_project_published_by FOREIGN KEY (published_by) REFERENCES sys_user (id)
);

CREATE TABLE project_creator (
    project_id BIGINT NOT NULL,
    creator_user_id BIGINT NOT NULL,
    assigned_by BIGINT NOT NULL,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (project_id, creator_user_id),
    CONSTRAINT fk_project_creator_project FOREIGN KEY (project_id) REFERENCES wedding_project (id),
    CONSTRAINT fk_project_creator_user FOREIGN KEY (creator_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_project_creator_assigned_by FOREIGN KEY (assigned_by) REFERENCES sys_user (id)
);

CREATE TABLE content_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT uk_content_category_name UNIQUE (name),
    CONSTRAINT fk_content_category_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id),
    CONSTRAINT fk_content_category_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id)
);

CREATE TABLE content_tag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT uk_content_tag_name UNIQUE (name),
    CONSTRAINT fk_content_tag_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id),
    CONSTRAINT fk_content_tag_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id)
);

CREATE TABLE work_collection (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(5000),
    category_id BIGINT NOT NULL,
    cover_photo_id BIGINT,
    visibility VARCHAR(32) NOT NULL DEFAULT 'HIDDEN',
    access_password_hash VARCHAR(255),
    review_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    publish_status VARCHAR(32) NOT NULL DEFAULT 'UNPUBLISHED',
    published_at TIMESTAMP NULL,
    published_by BIGINT,
    offline_reason VARCHAR(500),
    sort_order INT NOT NULL DEFAULT 0,
    is_featured BOOLEAN NOT NULL DEFAULT FALSE,
    is_pinned BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT fk_work_collection_project FOREIGN KEY (project_id) REFERENCES wedding_project (id),
    CONSTRAINT fk_work_collection_category FOREIGN KEY (category_id) REFERENCES content_category (id),
    CONSTRAINT fk_work_collection_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id),
    CONSTRAINT fk_work_collection_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id),
    CONSTRAINT fk_work_collection_published_by FOREIGN KEY (published_by) REFERENCES sys_user (id)
);

CREATE TABLE collection_creator (
    collection_id BIGINT NOT NULL,
    creator_user_id BIGINT NOT NULL,
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (collection_id, creator_user_id),
    CONSTRAINT fk_collection_creator_collection FOREIGN KEY (collection_id) REFERENCES work_collection (id),
    CONSTRAINT fk_collection_creator_user FOREIGN KEY (creator_user_id) REFERENCES sys_user (id)
);

CREATE TABLE collection_tag (
    collection_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (collection_id, tag_id),
    CONSTRAINT fk_collection_tag_collection FOREIGN KEY (collection_id) REFERENCES work_collection (id),
    CONSTRAINT fk_collection_tag_tag FOREIGN KEY (tag_id) REFERENCES content_tag (id)
);

CREATE INDEX idx_wedding_project_publish ON wedding_project (publish_status, published_at);
CREATE INDEX idx_wedding_project_region ON wedding_project (region_code, publish_status);
CREATE INDEX idx_wedding_project_created_by ON wedding_project (created_by, is_deleted, created_at);
CREATE INDEX idx_project_creator_user ON project_creator (creator_user_id, project_id);
CREATE INDEX idx_content_category_status ON content_category (status, is_deleted, sort_order);
CREATE INDEX idx_content_tag_status ON content_tag (status, is_deleted, sort_order);
CREATE INDEX idx_work_collection_project ON work_collection (project_id, is_deleted, created_at);
CREATE INDEX idx_work_collection_publish ON work_collection (publish_status, published_at);
CREATE INDEX idx_work_collection_category ON work_collection (category_id, publish_status);
CREATE INDEX idx_collection_creator_user ON collection_creator (creator_user_id, collection_id);
CREATE INDEX idx_collection_tag_tag ON collection_tag (tag_id, collection_id);
