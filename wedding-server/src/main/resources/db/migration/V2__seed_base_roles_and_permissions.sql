INSERT INTO system_role (code, name) VALUES
    ('ADMIN', '管理员'),
    ('CREATOR', '创作者'),
    ('CUSTOMER', '客户');

INSERT INTO system_permission (name, resource, parent_id, sort_order) VALUES
    ('工作台', '/dashboard', NULL, 10),
    ('婚礼项目', '/content/projects', NULL, 20),
    ('作品集', '/content/collections', NULL, 30),
    ('作品审核', '/review/collections', NULL, 40),
    ('创作者账号', '/accounts/creators', NULL, 50),
    ('客户账号', '/accounts/customers', NULL, 60),
    ('分类标签', '/config/content', NULL, 70),
    ('官网运营', '/site/home', NULL, 80),
    ('咨询线索', '/operations/inquiries', NULL, 90),
    ('数据统计', '/analytics', NULL, 100),
    ('操作审计', '/system/audit', NULL, 110);

