-- 用户推荐表
CREATE TABLE IF NOT EXISTS user_recommendation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    summary VARCHAR(500),
    cover_url VARCHAR(500),
    content_url VARCHAR(500),
    views INT DEFAULT 0,
    likes INT DEFAULT 0,
    status INT DEFAULT 1 COMMENT '0=草稿, 1=已发布',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 如果表已存在但没有 status 字段，执行以下语句添加
-- ALTER TABLE user_recommendation ADD COLUMN status INT DEFAULT 1 COMMENT '0=草稿, 1=已发布' AFTER likes;

-- 如果表已存在但 content_url 是 NOT NULL，执行以下语句修改
-- ALTER TABLE user_recommendation MODIFY COLUMN content_url VARCHAR(500);
