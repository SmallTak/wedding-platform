CREATE TABLE customer_notification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient_user_id BIGINT NOT NULL,
    type VARCHAR(64) NOT NULL,
    title VARCHAR(100) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    related_type VARCHAR(32),
    related_id BIGINT,
    read_at TIMESTAMP NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT fk_customer_notification_recipient FOREIGN KEY (recipient_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_customer_notification_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id),
    CONSTRAINT fk_customer_notification_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id)
);

CREATE INDEX idx_customer_notification_recipient_created
    ON customer_notification (recipient_user_id, is_deleted, created_at);

CREATE INDEX idx_customer_notification_recipient_unread
    ON customer_notification (recipient_user_id, is_deleted, read_at, created_at);
