CREATE TABLE project_customer_application (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    customer_user_id BIGINT NOT NULL,
    apply_note VARCHAR(1000) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    reviewed_by BIGINT,
    reviewed_at TIMESTAMP NULL,
    rejection_reason VARCHAR(500),
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT uk_project_customer_application UNIQUE (project_id, customer_user_id),
    CONSTRAINT fk_project_customer_application_project FOREIGN KEY (project_id) REFERENCES wedding_project (id),
    CONSTRAINT fk_project_customer_application_customer FOREIGN KEY (customer_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_project_customer_application_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES sys_user (id),
    CONSTRAINT fk_project_customer_application_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id),
    CONSTRAINT fk_project_customer_application_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id)
);

CREATE INDEX idx_project_customer_application_review
    ON project_customer_application (status, created_at);

CREATE INDEX idx_project_customer_application_customer
    ON project_customer_application (customer_user_id, status, created_at);
