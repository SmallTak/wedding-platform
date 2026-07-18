CREATE TABLE customer_feedback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    creator_user_id BIGINT NOT NULL,
    customer_user_id BIGINT,
    customer_display_name VARCHAR(100) NOT NULL,
    submitted_by BIGINT NOT NULL,
    rating INT NOT NULL,
    content VARCHAR(2000) NOT NULL,
    review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    rejection_reason VARCHAR(500),
    reviewed_by BIGINT,
    reviewed_at TIMESTAMP NULL,
    publish_status VARCHAR(32) NOT NULL DEFAULT 'UNPUBLISHED',
    published_at TIMESTAMP NULL,
    offline_reason VARCHAR(500),
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT fk_customer_feedback_project FOREIGN KEY (project_id) REFERENCES wedding_project (id),
    CONSTRAINT fk_customer_feedback_creator FOREIGN KEY (creator_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_customer_feedback_customer FOREIGN KEY (customer_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_customer_feedback_submitted_by FOREIGN KEY (submitted_by) REFERENCES sys_user (id),
    CONSTRAINT fk_customer_feedback_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES sys_user (id),
    CONSTRAINT fk_customer_feedback_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id),
    CONSTRAINT fk_customer_feedback_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id)
);

CREATE TABLE feedback_reply (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    feedback_id BIGINT NOT NULL,
    creator_user_id BIGINT NOT NULL,
    content VARCHAR(2000) NOT NULL,
    review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    rejection_reason VARCHAR(500),
    reviewed_by BIGINT,
    reviewed_at TIMESTAMP NULL,
    published_at TIMESTAMP NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT uk_feedback_reply_feedback UNIQUE (feedback_id),
    CONSTRAINT fk_feedback_reply_feedback FOREIGN KEY (feedback_id) REFERENCES customer_feedback (id),
    CONSTRAINT fk_feedback_reply_creator FOREIGN KEY (creator_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_feedback_reply_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES sys_user (id),
    CONSTRAINT fk_feedback_reply_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id),
    CONSTRAINT fk_feedback_reply_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id)
);

CREATE TABLE consultation_lead (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reference_code VARCHAR(32) NOT NULL,
    name VARCHAR(100) NOT NULL,
    contact VARCHAR(120) NOT NULL,
    wedding_date DATE,
    region VARCHAR(200),
    service_needs VARCHAR(1000) NOT NULL,
    remark VARCHAR(2000),
    follow_status VARCHAR(32) NOT NULL DEFAULT 'NEW',
    follow_note VARCHAR(2000),
    assigned_admin_id BIGINT,
    source VARCHAR(32) NOT NULL DEFAULT 'WEBSITE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT uk_consultation_lead_reference UNIQUE (reference_code),
    CONSTRAINT fk_consultation_lead_admin FOREIGN KEY (assigned_admin_id) REFERENCES sys_user (id)
);

CREATE TABLE homepage_feature (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    target_type VARCHAR(32) NOT NULL,
    target_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    is_pinned BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT uk_homepage_feature_target UNIQUE (target_type, target_id),
    CONSTRAINT fk_homepage_feature_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id),
    CONSTRAINT fk_homepage_feature_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id)
);

CREATE INDEX idx_customer_feedback_creator
    ON customer_feedback (creator_user_id, is_deleted, created_at);
CREATE INDEX idx_customer_feedback_review
    ON customer_feedback (review_status, publish_status, created_at);
CREATE INDEX idx_customer_feedback_project
    ON customer_feedback (project_id, publish_status, published_at);
CREATE INDEX idx_feedback_reply_review
    ON feedback_reply (review_status, created_at);
CREATE INDEX idx_consultation_lead_follow
    ON consultation_lead (follow_status, created_at);
CREATE INDEX idx_homepage_feature_order
    ON homepage_feature (target_type, status, is_pinned, sort_order);

INSERT INTO system_permission (name, resource, parent_id, sort_order)
VALUES ('客户评价', '/operations/feedback', NULL, 85);

INSERT INTO system_role_permission (role_id, permission_id)
SELECT sr.id, sp.id
FROM system_role sr
CROSS JOIN system_permission sp
WHERE sr.code IN ('ADMIN', 'CREATOR')
  AND sp.resource = '/operations/feedback';
