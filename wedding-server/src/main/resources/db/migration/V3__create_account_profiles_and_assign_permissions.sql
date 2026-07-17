CREATE TABLE creator_profile (
    user_id BIGINT PRIMARY KEY,
    introduction VARCHAR(1000),
    position_text VARCHAR(100),
    service_area VARCHAR(300),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_creator_profile_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
);

CREATE TABLE customer_profile (
    user_id BIGINT PRIMARY KEY,
    nickname VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_customer_profile_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
);

INSERT INTO professional_role (name, description, sort_order) VALUES
    ('摄影师', '婚礼、订婚及婚纱摄影', 10),
    ('化妆师', '新娘妆造及跟妆服务', 20),
    ('婚礼策划师', '婚礼策划、布置与执行', 30);

INSERT INTO system_role_permission (role_id, permission_id)
SELECT sr.id, sp.id
FROM system_role sr
CROSS JOIN system_permission sp
WHERE sr.code = 'ADMIN';

INSERT INTO system_role_permission (role_id, permission_id)
SELECT sr.id, sp.id
FROM system_role sr
CROSS JOIN system_permission sp
WHERE sr.code = 'CREATOR'
  AND sp.resource IN ('/dashboard', '/content/projects', '/content/collections');
