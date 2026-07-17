ALTER TABLE wedding_project
    ADD COLUMN rejection_reason VARCHAR(500);

ALTER TABLE wedding_project
    ADD COLUMN submitted_at TIMESTAMP NULL;

ALTER TABLE wedding_project
    ADD COLUMN reviewed_at TIMESTAMP NULL;

ALTER TABLE wedding_project
    ADD COLUMN reviewed_by BIGINT;

ALTER TABLE wedding_project
    ADD CONSTRAINT fk_wedding_project_reviewed_by
    FOREIGN KEY (reviewed_by) REFERENCES sys_user (id);

CREATE INDEX idx_wedding_project_review_queue
    ON wedding_project (review_status, submitted_at);

CREATE TABLE review_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    target_type VARCHAR(32) NOT NULL,
    target_id BIGINT NOT NULL,
    revision_no INT NOT NULL,
    submitted_by BIGINT NOT NULL,
    submitted_at TIMESTAMP NOT NULL,
    status VARCHAR(32) NOT NULL,
    CONSTRAINT uk_review_task_revision UNIQUE (target_type, target_id, revision_no),
    CONSTRAINT fk_review_task_submitted_by FOREIGN KEY (submitted_by) REFERENCES sys_user (id)
);

CREATE TABLE review_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    item_type VARCHAR(32) NOT NULL,
    business_id BIGINT,
    field_key VARCHAR(64),
    snapshot_json TEXT NOT NULL,
    revision_no INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    rejection_reason VARCHAR(500),
    reviewed_by BIGINT,
    reviewed_at TIMESTAMP NULL,
    is_current BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_review_item_task FOREIGN KEY (task_id) REFERENCES review_task (id),
    CONSTRAINT fk_review_item_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES sys_user (id)
);

CREATE TABLE review_action_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    review_item_id BIGINT,
    action VARCHAR(64) NOT NULL,
    operator_id BIGINT NOT NULL,
    reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_review_action_task FOREIGN KEY (task_id) REFERENCES review_task (id),
    CONSTRAINT fk_review_action_item FOREIGN KEY (review_item_id) REFERENCES review_item (id),
    CONSTRAINT fk_review_action_operator FOREIGN KEY (operator_id) REFERENCES sys_user (id)
);

CREATE INDEX idx_review_task_target
    ON review_task (target_type, target_id, revision_no);

CREATE INDEX idx_review_task_status
    ON review_task (status, submitted_at);

CREATE INDEX idx_review_item_task
    ON review_item (task_id, item_type, status);

CREATE INDEX idx_review_item_current_field
    ON review_item (item_type, field_key, is_current);

CREATE INDEX idx_review_item_current_business
    ON review_item (item_type, business_id, is_current);

CREATE INDEX idx_review_action_task
    ON review_action_log (task_id, created_at);
