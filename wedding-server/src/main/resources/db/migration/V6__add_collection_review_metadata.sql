ALTER TABLE work_collection
    ADD COLUMN rejection_reason VARCHAR(500);

ALTER TABLE work_collection
    ADD COLUMN submitted_at TIMESTAMP NULL;

ALTER TABLE work_collection
    ADD COLUMN reviewed_at TIMESTAMP NULL;

ALTER TABLE work_collection
    ADD COLUMN reviewed_by BIGINT;

ALTER TABLE work_collection
    ADD CONSTRAINT fk_work_collection_reviewed_by
    FOREIGN KEY (reviewed_by) REFERENCES sys_user (id);

CREATE INDEX idx_work_collection_review_queue
    ON work_collection (review_status, submitted_at);
