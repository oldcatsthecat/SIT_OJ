-- Run once against the existing sit_oj database before deploying the updated user service.
-- The default also initializes every existing row to false (0).
ALTER TABLE users
    ADD COLUMN locked TINYINT(1) NOT NULL DEFAULT 0 COMMENT '账户是否禁用' AFTER role;

SELECT COUNT(*) AS total_users,
       SUM(locked = 0) AS unlocked_users,
       SUM(locked = 1) AS locked_users
FROM users;
