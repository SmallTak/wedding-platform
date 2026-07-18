CREATE TABLE site_visit_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_date DATE NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    target_id BIGINT NOT NULL DEFAULT 0,
    visitor_hash VARCHAR(64) NOT NULL,
    session_bucket BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_site_visit_event_session
        UNIQUE (event_type, target_id, visitor_hash, session_bucket)
);

CREATE INDEX idx_site_visit_event_daily
    ON site_visit_event (event_date, event_type);

CREATE INDEX idx_site_visit_event_target
    ON site_visit_event (event_type, target_id, event_date);
