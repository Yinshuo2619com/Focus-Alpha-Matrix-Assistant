-- 课表功能数据库表

CREATE TABLE IF NOT EXISTS `schedule` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`       BIGINT       NOT NULL,
    `school_id`     VARCHAR(32)  NOT NULL DEFAULT 'default',
    `semester`      VARCHAR(32)  NOT NULL COMMENT '如 2025-2026-2',
    `academic_year` VARCHAR(16)  NOT NULL COMMENT '如 2025-2026',
    `start_date`    DATE         DEFAULT NULL COMMENT '学期起始日期',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `share_token`   VARCHAR(16)  DEFAULT NULL COMMENT '分享令牌',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_semester` (`user_id`, `school_id`, `semester`),
    UNIQUE KEY `uk_share_token` (`share_token`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `course_entry` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `schedule_id`   BIGINT       NOT NULL,
    `course_name`   VARCHAR(128) NOT NULL COMMENT '课程名称',
    `teacher`       VARCHAR(64)  DEFAULT NULL COMMENT '授课教师',
    `location`      VARCHAR(128) DEFAULT NULL COMMENT '上课地点',
    `day_of_week`   TINYINT      NOT NULL COMMENT '星期几 (1=周一 .. 7=周日)',
    `start_section` TINYINT      NOT NULL COMMENT '开始节次 (1-12)',
    `end_section`   TINYINT      NOT NULL COMMENT '结束节次',
    `weeks`         VARCHAR(128) NOT NULL COMMENT '周次范围, 如 1-16 或 1,3,5',
    `color`         VARCHAR(16)  DEFAULT NULL COMMENT '前端渲染颜色 (hex)',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_schedule_id` (`schedule_id`),
    CONSTRAINT `fk_course_schedule` FOREIGN KEY (`schedule_id`) REFERENCES `schedule` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `school_config` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `school_id`     VARCHAR(32)  NOT NULL COMMENT '学校标识',
    `school_name`   VARCHAR(64)  NOT NULL COMMENT '显示名称',
    `base_url`      VARCHAR(256) NOT NULL COMMENT '教务系统基础URL',
    `login_path`    VARCHAR(128) NOT NULL DEFAULT '/login' COMMENT '登录页路径',
    `schedule_path` VARCHAR(128) NOT NULL DEFAULT '/student/for-std/course-table' COMMENT '课表查询页路径',
    `enabled`       TINYINT      NOT NULL DEFAULT 1,
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_school_id` (`school_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 插入默认学校配置（请修改为实际的教务系统地址）
INSERT IGNORE INTO `school_config` (`school_id`, `school_name`, `base_url`, `login_path`, `schedule_path`, `enabled`)
VALUES ('default', '默认学校', 'https://jwxt.aqnu.edu.cn', '/student/login', '/student/for-std/course-table', 1);
