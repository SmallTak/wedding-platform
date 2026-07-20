ALTER TABLE work_collection
    ADD COLUMN event_date DATE NULL;

ALTER TABLE work_collection
    ADD COLUMN region_code VARCHAR(64) NULL;

ALTER TABLE work_collection
    ADD COLUMN location_text VARCHAR(300) NULL;

UPDATE work_collection
SET event_date = (
        SELECT project.event_date
        FROM wedding_project project
        WHERE project.id = work_collection.project_id
    ),
    region_code = (
        SELECT project.region_code
        FROM wedding_project project
        WHERE project.id = work_collection.project_id
    ),
    location_text = (
        SELECT project.location_text
        FROM wedding_project project
        WHERE project.id = work_collection.project_id
    )
WHERE project_id IS NOT NULL;

ALTER TABLE homepage_carousel_item
    ADD COLUMN photo_id BIGINT NULL;

UPDATE homepage_carousel_item
SET photo_id = (
    SELECT collection.cover_photo_id
    FROM work_collection collection
    WHERE collection.id = homepage_carousel_item.collection_id
)
WHERE collection_id IS NOT NULL;

DELETE FROM homepage_carousel_item
WHERE photo_id IS NULL;

ALTER TABLE homepage_carousel_item
    MODIFY photo_id BIGINT NOT NULL;

ALTER TABLE homepage_carousel_item
    DROP FOREIGN KEY fk_homepage_carousel_collection;

ALTER TABLE homepage_carousel_item
    DROP INDEX uk_homepage_carousel_collection;

ALTER TABLE homepage_carousel_item
    DROP COLUMN collection_id;

ALTER TABLE homepage_carousel_item
    ADD CONSTRAINT uk_homepage_carousel_photo UNIQUE (photo_id);

ALTER TABLE homepage_carousel_item
    ADD CONSTRAINT fk_homepage_carousel_photo
    FOREIGN KEY (photo_id) REFERENCES collection_photo (id);

ALTER TABLE customer_feedback
    ADD COLUMN collection_id BIGINT NULL;

UPDATE customer_feedback
SET collection_id = (
    SELECT collection.id
    FROM work_collection collection
    WHERE collection.project_id = customer_feedback.project_id
      AND collection.is_deleted = FALSE
    ORDER BY collection.published_at DESC, collection.created_at DESC, collection.id DESC
    LIMIT 1
)
WHERE project_id IS NOT NULL;

DELETE FROM feedback_reply
WHERE feedback_id IN (
    SELECT id
    FROM customer_feedback
    WHERE collection_id IS NULL
);

DELETE FROM customer_feedback
WHERE collection_id IS NULL;

ALTER TABLE customer_feedback
    MODIFY collection_id BIGINT NOT NULL;

ALTER TABLE customer_feedback
    DROP FOREIGN KEY fk_customer_feedback_project;

ALTER TABLE customer_feedback
    DROP INDEX idx_customer_feedback_project;

ALTER TABLE customer_feedback
    DROP COLUMN project_id;

ALTER TABLE customer_feedback
    ADD CONSTRAINT fk_customer_feedback_collection
    FOREIGN KEY (collection_id) REFERENCES work_collection (id);

CREATE INDEX idx_customer_feedback_collection
    ON customer_feedback (collection_id, publish_status, published_at);

DELETE FROM review_action_log
WHERE task_id IN (
    SELECT id
    FROM review_task
    WHERE target_type = 'PROJECT'
);

DELETE FROM review_item
WHERE task_id IN (
    SELECT id
    FROM review_task
    WHERE target_type = 'PROJECT'
);

DELETE FROM review_task
WHERE target_type = 'PROJECT';

DELETE FROM site_visit_event
WHERE event_type = 'PROJECT';

UPDATE user_notification
SET type = 'LEGACY',
    title = CASE
        WHEN title IS NULL OR title = '' THEN '历史项目消息'
        WHEN title LIKE '历史项目消息：%' THEN title
        ELSE CONCAT('历史项目消息：', title)
    END,
    related_type = NULL,
    related_id = NULL
WHERE type IN (
    'PROJECT_LINK_APPROVED',
    'PROJECT_LINK_REJECTED',
    'PROJECT_REVIEW_TASK',
    'PROJECT_REVIEW_APPROVED',
    'PROJECT_REVIEW_REJECTED',
    'PROJECT_PARTICIPANT_ADDED',
    'PROJECT_PARTICIPANT_REMOVED',
    'PROJECT_PUBLISHED',
    'PROJECT_OFFLINE',
    'CUSTOMER_PROJECT_APPLICATION_NEW'
);

UPDATE user_notification
SET related_type = NULL,
    related_id = NULL
WHERE related_type IN ('PROJECT_APPLICATION', 'PROJECT', 'PROJECT_REVIEW');

DELETE FROM homepage_feature
WHERE target_type IN ('PROJECT', 'COLLECTION');

DROP TABLE project_customer_application;

ALTER TABLE work_collection
    DROP FOREIGN KEY fk_work_collection_project;

ALTER TABLE work_collection
    DROP INDEX idx_work_collection_project;

ALTER TABLE work_collection
    DROP COLUMN project_id;

DROP TABLE project_creator;

ALTER TABLE wedding_project
    DROP FOREIGN KEY fk_wedding_project_reviewed_by;

ALTER TABLE wedding_project
    DROP INDEX idx_wedding_project_review_queue;

DROP TABLE wedding_project;

DELETE FROM system_role_permission
WHERE permission_id IN (
    SELECT id
    FROM system_permission
    WHERE resource = '/content/projects'
);

DELETE FROM system_permission
WHERE resource = '/content/projects';
