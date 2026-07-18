ALTER TABLE customer_notification RENAME TO user_notification;

INSERT INTO system_permission (name, resource, parent_id, sort_order)
VALUES ('站内消息', '/notifications', NULL, 15);

INSERT INTO system_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM system_role role
CROSS JOIN system_permission permission
WHERE role.code IN ('ADMIN', 'CREATOR')
  AND permission.resource = '/notifications';
