CREATE TABLE sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mobile VARCHAR(32) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100),
    avatar_path VARCHAR(500),
    account_type VARCHAR(32) NOT NULL,
    account_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    must_change_password BOOLEAN NOT NULL DEFAULT TRUE,
    profile_completed BOOLEAN NOT NULL DEFAULT FALSE,
    last_login_at TIMESTAMP NULL,
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT uk_sys_user_mobile UNIQUE (mobile)
);

CREATE TABLE system_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_system_role_code UNIQUE (code)
);

CREATE TABLE system_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    resource VARCHAR(200) NOT NULL,
    parent_id BIGINT,
    route VARCHAR(200),
    component VARCHAR(300),
    icon VARCHAR(100),
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_system_permission_resource UNIQUE (resource)
);

CREATE TABLE system_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_system_user_role UNIQUE (user_id, role_id),
    CONSTRAINT fk_system_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_system_user_role_role FOREIGN KEY (role_id) REFERENCES system_role (id)
);

CREATE TABLE system_role_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_system_role_permission UNIQUE (role_id, permission_id),
    CONSTRAINT fk_system_role_permission_role FOREIGN KEY (role_id) REFERENCES system_role (id),
    CONSTRAINT fk_system_role_permission_permission FOREIGN KEY (permission_id) REFERENCES system_permission (id)
);

CREATE TABLE professional_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_professional_role_name UNIQUE (name)
);

CREATE TABLE creator_professional_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    creator_user_id BIGINT NOT NULL,
    professional_role_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_creator_professional_role UNIQUE (creator_user_id, professional_role_id),
    CONSTRAINT fk_creator_professional_role_user FOREIGN KEY (creator_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_creator_professional_role_role FOREIGN KEY (professional_role_id) REFERENCES professional_role (id)
);

CREATE TABLE operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    operator_id BIGINT,
    operator_type VARCHAR(32),
    module VARCHAR(100) NOT NULL,
    action VARCHAR(100) NOT NULL,
    business_type VARCHAR(100),
    business_id BIGINT,
    before_snapshot TEXT,
    after_snapshot TEXT,
    reason VARCHAR(500),
    ip_address VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sys_user_status ON sys_user (account_status, is_deleted);
CREATE INDEX idx_system_permission_parent ON system_permission (parent_id, sort_order);
CREATE INDEX idx_operation_log_business ON operation_log (business_type, business_id, created_at);

