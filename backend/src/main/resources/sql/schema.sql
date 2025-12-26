-- =============================================
-- 大屏网格化编排 - 数据库表
-- =============================================

-- 大屏配置表
CREATE TABLE IF NOT EXISTS `monitor_dashboard`
(
    `id`                BIGINT PRIMARY KEY AUTO_INCREMENT,
    `name`              VARCHAR(100) NOT NULL COMMENT '大屏名称',
    `grid_cols`         INT      DEFAULT 24 COMMENT '网格列数',
    `grid_rows`         INT      DEFAULT 12 COMMENT '网格行数',
    `cell_height`       INT      DEFAULT 60 COMMENT '单元格高度(px)',
    `widget_margin`     INT      DEFAULT 10 COMMENT '组件间隔',
    `background_config` JSON COMMENT '背景配置(颜色/图片/主题)',
    `is_default`        TINYINT  DEFAULT 0 COMMENT '是否默认大屏',
    `is_active`         TINYINT  DEFAULT 1 COMMENT '是否启用',
    `create_time`       DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time`       DATETIME ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='大屏配置表';

-- 大屏组件布局表
CREATE TABLE IF NOT EXISTS `monitor_dashboard_widget`
(
    `id`             BIGINT PRIMARY KEY AUTO_INCREMENT,
    `dashboard_id`   BIGINT          NOT NULL COMMENT '所属大屏ID',
    `widget_type`    VARCHAR(20)     DEFAULT 'TASK' COMMENT '组件类型: TASK/WORKFLOW/CLOCK/TEXT/IMAGE',
    `source_id`      BIGINT          COMMENT '关联的任务/工作流ID',
    `title`          VARCHAR(100)    COMMENT '组件标题(覆盖默认)',
    `grid_x`         INT             NOT NULL DEFAULT 0 COMMENT '网格起始X(0-based)',
    `grid_y`         INT             NOT NULL DEFAULT 0 COMMENT '网格起始Y(0-based)',
    `grid_w`         INT             DEFAULT 4 COMMENT '占用宽度(网格数)',
    `grid_h`         INT             DEFAULT 3 COMMENT '占用高度(网格数)',
    `display_config` JSON            COMMENT '显示配置(刷新频率/主题等)',
    `z_index`        INT             DEFAULT 0 COMMENT '层级',
    `create_time`    DATETIME        DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_dashboard_id` (`dashboard_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='大屏组件布局表';

-- 插入默认大屏
-- INSERT INTO monitor_dashboard (name, grid_cols, grid_rows, cell_height, is_default, is_active) VALUES ('默认监控大屏', 24, 12, 60, 1, 1);


-- 业务监控系统表结构
-- 1. 数据源配置表
CREATE TABLE IF NOT EXISTS `monitor_datasource`
(
    `id`                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name`               VARCHAR(100) NOT NULL COMMENT '数据源名称',
    `url`                VARCHAR(500) NOT NULL COMMENT 'JDBC URL',
    `username`           VARCHAR(100) NOT NULL COMMENT '用户名',
    `password_encrypted` VARCHAR(500) COMMENT '密码(AES加密存储)',
    `driver_class_name`  VARCHAR(100) DEFAULT 'com.mysql.cj.jdbc.Driver' COMMENT '驱动类名',
    `create_time`        DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time`        DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='外部数据源配置';

-- ==================== 系统用户表 ====================
CREATE TABLE IF NOT EXISTS `sys_user`
(
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
    `username`      VARCHAR(50)  NOT NULL UNIQUE COMMENT '用户名',
    `password_hash` VARCHAR(255) NOT NULL COMMENT '密码哈希(BCrypt)',
    `role`          VARCHAR(20) DEFAULT 'USER' COMMENT '角色: ADMIN/USER',
    `enabled`       TINYINT(1)  DEFAULT 1 COMMENT '是否启用',
    `create_time`   DATETIME    DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='系统用户';

-- 插入默认管理员账户 (密码: admin123)
-- INSERT IGNORE INTO `sys_user` (`username`, `password_hash`, `role`, `enabled`) VALUES ('admin', '$2a$10$wIp9iDdYOjyPftT1H1X2pu9jde6aVKxP7UAg.DZaQu5IcMbAXdXRe', 'ADMIN', 1);

-- 2. 监控任务表
CREATE TABLE IF NOT EXISTS `monitor_task`
(
    `id`                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name`                 VARCHAR(100) NOT NULL COMMENT '任务名称',
    `datasource_id`        BIGINT       NOT NULL COMMENT '关联的数据源ID',
    `sql_script`           TEXT         NOT NULL COMMENT '监控SQL',
    `cron_expression`      VARCHAR(100) NOT NULL COMMENT 'Cron表达式',
    `is_active`            TINYINT(1)  DEFAULT 1 COMMENT '是否启用: 1-启用, 0-停用',
    `result_type`          VARCHAR(20) DEFAULT 'SCALAR' COMMENT '结果类型: SCALAR(数值), DATASET(列表)',
    `alarm_threshold_rule` TEXT COMMENT '报警规则JSON (e.g. {"operator":">", "value":10})',
    `chart_config`         TEXT COMMENT '可视化配置JSON (e.g. {"type": "bar", "xAxis": "status", "yAxis": "count"})',
    `is_store_data`        TINYINT(1)  DEFAULT 0 COMMENT '是否开启结果存储',
    `create_time`          DATETIME    DEFAULT CURRENT_TIMESTAMP,
    `update_time`          DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='监控任务配置';

-- 3. 监控记录表
CREATE TABLE IF NOT EXISTS `monitor_record`
(
    `id`             BIGINT AUTO_INCREMENT PRIMARY KEY,
    `task_id`        BIGINT NOT NULL,
    `execution_time` DATETIME   DEFAULT CURRENT_TIMESTAMP,
    `is_success`     TINYINT(1) DEFAULT 1,
    `error_message`  TEXT,
    `result_number`  DOUBLE COMMENT '数值结果(如果是数值型)',
    `result_json`    LONGTEXT COMMENT '列表结果(如果是列表型)',
    INDEX `idx_task_time` (`task_id`, `execution_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='监控历史记录';

-- 4. 报警通道配置表
CREATE TABLE IF NOT EXISTS `alarm_channel`
(
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name`        VARCHAR(100) NOT NULL COMMENT '通道名称',
    `type`        VARCHAR(20)  NOT NULL COMMENT '类型: EMAIL, SMS, DINGTALK, ANNOUNCEMENT',
    `config`      TEXT         NOT NULL COMMENT '配置JSON',
    `is_active`   TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    `create_time` DATETIME   DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME   DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='报警通道配置';

-- 5. 报警模板表
CREATE TABLE IF NOT EXISTS `alarm_template`
(
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name`        VARCHAR(100) NOT NULL COMMENT '模板名称',
    `subject`     VARCHAR(200) COMMENT '标题模板',
    `content`     TEXT         NOT NULL COMMENT '内容模板，支持变量: ${taskName}, ${value}, ${threshold}, ${time}',
    `is_default`  TINYINT(1) DEFAULT 0 COMMENT '是否默认模板',
    `create_time` DATETIME   DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME   DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='报警模板';

-- 6. 报警历史记录表
CREATE TABLE IF NOT EXISTS `alarm_history`
(
    `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
    `task_id`         BIGINT NOT NULL,
    `channel_id`      BIGINT NOT NULL,
    `trigger_time`    DATETIME   DEFAULT CURRENT_TIMESTAMP,
    `trigger_type`    VARCHAR(20) COMMENT 'THRESHOLD, YOY, MOM',
    `trigger_value`   DOUBLE COMMENT '触发时的值',
    `threshold_value` DOUBLE COMMENT '阈值',
    `message`         TEXT COMMENT '发送的消息内容',
    `is_success`      TINYINT(1) DEFAULT 1,
    `error_message`   TEXT,
    INDEX `idx_task_time` (`task_id`, `trigger_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='报警历史记录';

-- 7. 服务器分组表
CREATE TABLE IF NOT EXISTS `server_group`
(
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name`        VARCHAR(100) NOT NULL COMMENT '分组名称',
    `description` VARCHAR(500) COMMENT '描述',
    `parent_id`   BIGINT COMMENT '父分组ID',
    `is_active`   TINYINT(1) DEFAULT 1,
    `create_time` DATETIME   DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME   DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='服务器分组';

-- 8. 服务器资产表
CREATE TABLE IF NOT EXISTS `server_asset`
(
    `id`                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name`                  VARCHAR(100) NOT NULL COMMENT '主机名称',
    `ip`                    VARCHAR(50)  NOT NULL COMMENT 'IP地址',
    `port`                  INT         DEFAULT 22 COMMENT 'SSH端口',
    `username`              VARCHAR(50) COMMENT 'SSH用户名',
    `auth_type`             VARCHAR(20) DEFAULT 'PASSWORD' COMMENT '认证方式: PASSWORD/KEY',
    `password_encrypted`    TEXT COMMENT '密码(AES加密)',
    `private_key_encrypted` TEXT COMMENT '私钥(AES加密)',
    `group_id`              BIGINT COMMENT '所属分组',
    `tags`                  VARCHAR(500) COMMENT '标签JSON',
    `is_active`             TINYINT(1)  DEFAULT 1,
    `last_check_time`       DATETIME COMMENT '最后检测时间',
    `last_check_status`     VARCHAR(20) COMMENT '状态: ONLINE/OFFLINE',
    `create_time`           DATETIME    DEFAULT CURRENT_TIMESTAMP,
    `update_time`           DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_group` (`group_id`),
    INDEX `idx_ip` (`ip`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='服务器资产';

-- 9. 监控模板表
CREATE TABLE IF NOT EXISTS `monitor_template`
(
    `id`                BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name`              VARCHAR(100) NOT NULL COMMENT '模板名称',
    `category`          VARCHAR(50)  NOT NULL COMMENT '分类: BASIC/COMPONENT/APPLICATION',
    `sub_category`      VARCHAR(50) COMMENT '子分类: CPU/MySQL/Redis等',
    `collect_type`      VARCHAR(20)  NOT NULL COMMENT '采集方式: SSH_SCRIPT/API/SQL',
    `collect_script`    TEXT COMMENT '采集脚本',
    `param_schema`      TEXT COMMENT '参数定义JSON',
    `default_threshold` TEXT COMMENT '默认阈值JSON',
    `default_cron`      VARCHAR(50) DEFAULT '0 */5 * * * ?' COMMENT '默认Cron',
    `alarm_template_id` BIGINT COMMENT '关联的告警模板ID',
    `description`       TEXT COMMENT '说明',
    `is_system`         TINYINT(1)  DEFAULT 0 COMMENT '是否系统内置',
    `is_active`         TINYINT(1)  DEFAULT 1,
    `sort_order`        INT         DEFAULT 0,
    `create_time`       DATETIME    DEFAULT CURRENT_TIMESTAMP,
    `update_time`       DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='监控模板';

-- -- 插入预置监控模板
-- INSERT INTO `monitor_template` (`name`, `category`, `sub_category`, `collect_type`, `collect_script`, `default_threshold`, `alarm_template`, `description`, `is_system`) VALUES
-- ('CPU使用率', 'BASIC', 'CPU', 'SSH_SCRIPT', 'top -bn1 | grep "Cpu(s)" | awk ''{print $2}'' | cut -d. -f1', '{"operator":">","value":50}', '${serverName}(${ip}) CPU使用率${value}%，超过阈值${threshold}%', 'CPU使用率监控', 1),
-- ('内存使用率', 'BASIC', 'MEMORY', 'SSH_SCRIPT', 'free | grep Mem | awk ''{printf("%.0f", $3/$2*100)}''', '{"operator":">","value":70}', '${serverName}(${ip}) 内存使用率${value}%，超过阈值${threshold}%', '内存使用率监控', 1),
-- ('磁盘使用率', 'BASIC', 'DISK', 'SSH_SCRIPT', 'df -h ${path} | tail -1 | awk ''{print $5}'' | tr -d ''%''', '{"operator":">","value":70}', '${serverName}(${ip}) 磁盘${path}使用率${value}%，超过阈值${threshold}%', '磁盘使用率监控', 1),
-- ('网络连通性', 'BASIC', 'NETWORK', 'SSH_SCRIPT', 'ping -c 3 ${target} > /dev/null 2>&1 && echo 1 || echo 0', '{"operator":"=","value":0}', '${serverName}(${ip}) 到${target}网络不通', '网络连通性检测', 1),
-- ('TCP连接数', 'BASIC', 'NETWORK', 'SSH_SCRIPT', 'netstat -an | grep ESTABLISHED | wc -l', '{"operator":">","value":7000}', '${serverName}(${ip}) TCP连接数${value}，超过阈值${threshold}', 'TCP连接数监控', 1),
-- ('端口探测', 'COMPONENT', 'PORT', 'SSH_SCRIPT', 'nc -v -w 3 -z ${target_ip} ${target_port} > /dev/null 2>&1 && echo 1 || echo 0', '{"operator":"=","value":0}', '${serverName}(${ip}) 端口${target_ip}:${target_port}不可达', '端口探测', 1),
-- ('HTTP可用性', 'APPLICATION', 'HTTP', 'SSH_SCRIPT', 'curl -o /dev/null -s -w "%{http_code}" ${url}', '{"operator":"!=","value":200}', '${serverName}(${ip}) HTTP请求${url}返回${value}，非200', 'HTTP可用性检测', 1),
-- ('应用日志错误', 'APPLICATION', 'LOG', 'SSH_SCRIPT', 'tail -100 ${log_path} | grep -c ERROR || echo 0', '{"operator":">","value":0}', '${serverName}(${ip}) 日志${log_path}发现${value}个ERROR', '应用日志错误检测', 1);

-- 10. 服务器监控任务表（关联服务器与监控模板）
CREATE TABLE IF NOT EXISTS `server_monitor_task`
(
    `id`                BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name`              VARCHAR(100) NOT NULL COMMENT '任务名称',
    `server_id`         BIGINT       NOT NULL COMMENT '服务器ID',
    `template_id`       BIGINT COMMENT '监控模板ID（可选，自定义时为空）',
    `alarm_template_id` BIGINT COMMENT '告警模板ID',
    `collect_script`    TEXT COMMENT '采集脚本（自定义或覆盖模板）',
    `params`            TEXT COMMENT '参数JSON（如端口号、路径等）',
    `threshold_rule`    TEXT COMMENT '阈值规则JSON',
    `cron_expression`   VARCHAR(50) DEFAULT '0 */5 * * * ?' COMMENT 'Cron表达式',
    `alarm_channels`    VARCHAR(200) COMMENT '告警通道ID列表，逗号分隔',
    `is_active`         TINYINT(1)  DEFAULT 1,
    `last_run_time`     DATETIME COMMENT '最后执行时间',
    `last_run_status`   VARCHAR(20) COMMENT '最后状态: SUCCESS/FAILED',
    `last_run_value`    VARCHAR(100) COMMENT '最后采集值',
    `create_time`       DATETIME    DEFAULT CURRENT_TIMESTAMP,
    `update_time`       DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_server` (`server_id`),
    INDEX `idx_template` (`template_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='服务器监控任务';

-- -- 初始化监控模板（基础监控 + 组件监控）
-- INSERT IGNORE INTO `monitor_template` (`id`, `name`, `category`, `sub_category`, `collect_type`, `collect_script`, `param_schema`, `default_threshold`, `alarm_template`, `description`, `is_system`, `sort_order`) VALUES
-- -- 基础监控
-- (1, 'CPU使用率', 'BASIC', 'CPU', 'SSH_SCRIPT', 'top -bn1 | grep "Cpu(s)" | awk ''{print $2}'' | cut -d. -f1', NULL, '{"operator":">","value":50}', '${serverName}(${ip}) CPU使用率${value}%，超过阈值${threshold}%', 'CPU使用率监控，阈值默认50%', 1, 1),
-- (2, '内存使用率', 'BASIC', 'MEMORY', 'SSH_SCRIPT', 'free | grep Mem | awk ''{printf("%.0f", $3/$2*100)}''', NULL, '{"operator":">","value":70}', '${serverName}(${ip}) 内存使用率${value}%，超过阈值${threshold}%', '内存使用率监控，阈值默认70%', 1, 2),
-- (3, '磁盘使用率', 'BASIC', 'DISK', 'SSH_SCRIPT', 'df -h / | tail -1 | awk ''{print $5}'' | tr -d ''%''', '{"path":{"type":"string","default":"/"}}', '{"operator":">","value":70}', '${serverName}(${ip}) 磁盘使用率${value}%，超过阈值${threshold}%', '磁盘使用率监控，可配置路径', 1, 3),
-- (4, 'TCP连接数', 'BASIC', 'NETWORK', 'SSH_SCRIPT', 'netstat -an | grep ESTABLISHED | wc -l', NULL, '{"operator":">","value":7000}', '${serverName}(${ip}) TCP连接数${value}，超过阈值${threshold}', 'TCP连接数监控', 1, 4),
-- (5, '系统负载', 'BASIC', 'LOAD', 'SSH_SCRIPT', 'cat /proc/loadavg | awk ''{print $1}''', NULL, '{"operator":">","value":10}', '${serverName}(${ip}) 系统负载${value}，超过阈值${threshold}', '系统1分钟平均负载', 1, 5),
-- -- 组件监控 - 常见端口
-- (10, 'MySQL端口', 'COMPONENT', 'MySQL', 'SSH_SCRIPT', 'nc -v -w 3 -z 127.0.0.1 3306 > /dev/null 2>&1 && echo 1 || echo 0', '{"port":{"type":"number","default":3306}}', '{"operator":"=","value":0}', '${serverName}(${ip}) MySQL端口3306不可达', 'MySQL默认端口3306监控', 1, 10),
-- (11, 'Redis端口', 'COMPONENT', 'Redis', 'SSH_SCRIPT', 'nc -v -w 3 -z 127.0.0.1 6379 > /dev/null 2>&1 && echo 1 || echo 0', '{"port":{"type":"number","default":6379}}', '{"operator":"=","value":0}', '${serverName}(${ip}) Redis端口6379不可达', 'Redis默认端口6379监控', 1, 11),
-- (12, 'Nginx端口', 'COMPONENT', 'Nginx', 'SSH_SCRIPT', 'nc -v -w 3 -z 127.0.0.1 80 > /dev/null 2>&1 && echo 1 || echo 0', '{"port":{"type":"number","default":80}}', '{"operator":"=","value":0}', '${serverName}(${ip}) Nginx端口80不可达', 'Nginx默认端口80监控', 1, 12),
-- (13, 'MongoDB端口', 'COMPONENT', 'MongoDB', 'SSH_SCRIPT', 'nc -v -w 3 -z 127.0.0.1 27017 > /dev/null 2>&1 && echo 1 || echo 0', '{"port":{"type":"number","default":27017}}', '{"operator":"=","value":0}', '${serverName}(${ip}) MongoDB端口27017不可达', 'MongoDB默认端口27017监控', 1, 13),
-- (14, 'Kafka端口', 'COMPONENT', 'Kafka', 'SSH_SCRIPT', 'nc -v -w 3 -z 127.0.0.1 9092 > /dev/null 2>&1 && echo 1 || echo 0', '{"port":{"type":"number","default":9092}}', '{"operator":"=","value":0}', '${serverName}(${ip}) Kafka端口9092不可达', 'Kafka默认端口9092监控', 1, 14),
-- (15, 'Elasticsearch端口', 'COMPONENT', 'ES', 'SSH_SCRIPT', 'nc -v -w 3 -z 127.0.0.1 9200 > /dev/null 2>&1 && echo 1 || echo 0', '{"port":{"type":"number","default":9200}}', '{"operator":"=","value":0}', '${serverName}(${ip}) ES端口9200不可达', 'Elasticsearch默认端口9200监控', 1, 15),
-- (16, 'Zookeeper端口', 'COMPONENT', 'Zookeeper', 'SSH_SCRIPT', 'nc -v -w 3 -z 127.0.0.1 2181 > /dev/null 2>&1 && echo 1 || echo 0', '{"port":{"type":"number","default":2181}}', '{"operator":"=","value":0}', '${serverName}(${ip}) Zookeeper端口2181不可达', 'Zookeeper默认端口2181监控', 1, 16),
-- (17, 'RabbitMQ端口', 'COMPONENT', 'RabbitMQ', 'SSH_SCRIPT', 'nc -v -w 3 -z 127.0.0.1 5672 > /dev/null 2>&1 && echo 1 || echo 0', '{"port":{"type":"number","default":5672}}', '{"operator":"=","value":0}', '${serverName}(${ip}) RabbitMQ端口5672不可达', 'RabbitMQ默认端口5672监控', 1, 17),
-- (18, 'MinIO端口', 'COMPONENT', 'MinIO', 'SSH_SCRIPT', 'nc -v -w 3 -z 127.0.0.1 9000 > /dev/null 2>&1 && echo 1 || echo 0', '{"port":{"type":"number","default":9000}}', '{"operator":"=","value":0}', '${serverName}(${ip}) MinIO端口9000不可达', 'MinIO默认端口9000监控', 1, 18),
-- (19, 'Hadoop端口', 'COMPONENT', 'Hadoop', 'SSH_SCRIPT', 'nc -v -w 3 -z 127.0.0.1 9870 > /dev/null 2>&1 && echo 1 || echo 0', '{"port":{"type":"number","default":9870}}', '{"operator":"=","value":0}', '${serverName}(${ip}) Hadoop端口9870不可达', 'Hadoop NameNode默认端口9870监控', 1, 19),
-- (20, 'FTP端口', 'COMPONENT', 'FTP', 'SSH_SCRIPT', 'nc -v -w 3 -z 127.0.0.1 21 > /dev/null 2>&1 && echo 1 || echo 0', '{"port":{"type":"number","default":21}}', '{"operator":"=","value":0}', '${serverName}(${ip}) FTP端口21不可达', 'FTP默认端口21监控', 1, 20),
-- -- 应用监控
-- (30, 'HTTP可用性', 'APPLICATION', 'HTTP', 'SSH_SCRIPT', 'curl -o /dev/null -s -w "%{http_code}" http://127.0.0.1:${port}', '{"port":{"type":"number","default":8080},"url":{"type":"string"}}', '{"operator":"!=","value":200}', '${serverName}(${ip}) HTTP请求返回${value}，非200', 'HTTP可用性检测', 1, 30),
-- (31, '应用日志错误', 'APPLICATION', 'LOG', 'SSH_SCRIPT', 'tail -100 ${log_path} | grep -c ERROR || echo 0', '{"log_path":{"type":"string","default":"/var/log/app.log"}}', '{"operator":">","value":0}', '${serverName}(${ip}) 日志发现${value}个ERROR', '应用日志错误检测', 1, 31);

-- ==================== 告警模块扩展表 ====================

-- 活跃告警表（当前未恢复的告警）
CREATE TABLE IF NOT EXISTS `alarm_active`
(
    `id`                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    `task_id`            BIGINT      NOT NULL COMMENT '关联的监控任务ID',
    `task_type`          VARCHAR(20) NOT NULL COMMENT '任务类型: MONITOR_TASK/SERVER_TASK/WORKFLOW',
    `trigger_type`       VARCHAR(20) NOT NULL COMMENT '触发类型: THRESHOLD/YOY/MOM',
    `trigger_value`      DOUBLE COMMENT '触发时的值',
    `threshold_value`    DOUBLE COMMENT '阈值',
    `first_trigger_time` DATETIME    NOT NULL COMMENT '首次触发时间',
    `last_trigger_time`  DATETIME    NOT NULL COMMENT '最后触发时间',
    `trigger_count`      INT         DEFAULT 1 COMMENT '触发次数',
    `status`             VARCHAR(20) DEFAULT 'FIRING' COMMENT '状态: FIRING/ACKNOWLEDGED/SUPPRESSED/RESOLVED',
    `level`              VARCHAR(20) DEFAULT 'WARNING' COMMENT '级别: INFO/WARNING/CRITICAL',
    `acknowledge_by`     VARCHAR(100) COMMENT '确认人',
    `acknowledge_time`   DATETIME COMMENT '确认时间',
    `resolve_time`       DATETIME COMMENT '恢复时间',
    `suppressed_until`   DATETIME COMMENT '抑制截止时间',
    `message`            TEXT COMMENT '告警消息',
    `create_time`        DATETIME    DEFAULT CURRENT_TIMESTAMP,
    `update_time`        DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_task` (`task_id`, `task_type`),
    INDEX `idx_status` (`status`),
    INDEX `idx_last_trigger` (`last_trigger_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='活跃告警';

-- 告警抑制规则表
CREATE TABLE IF NOT EXISTS `alarm_suppression_rule`
(
    `id`                        BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name`                      VARCHAR(100) NOT NULL COMMENT '规则名称',
    `match_type`                VARCHAR(20)  NOT NULL COMMENT '匹配类型: TASK/TASK_TYPE/CHANNEL',
    `match_value`               VARCHAR(200) COMMENT '匹配值',
    `suppress_duration_minutes` INT        DEFAULT 30 COMMENT '抑制时长(分钟)',
    `max_suppress_count`        INT        DEFAULT 5 COMMENT '最大抑制次数后强制发送',
    `is_active`                 TINYINT(1) DEFAULT 1,
    `create_time`               DATETIME   DEFAULT CURRENT_TIMESTAMP,
    `update_time`               DATETIME   DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='告警抑制规则';

-- 告警升级规则表
CREATE TABLE IF NOT EXISTS `alarm_escalation_rule`
(
    `id`                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name`                VARCHAR(100) NOT NULL COMMENT '规则名称',
    `after_minutes`       INT          NOT NULL COMMENT '未确认N分钟后升级',
    `from_level`          VARCHAR(20)  NOT NULL COMMENT '原级别',
    `to_level`            VARCHAR(20)  NOT NULL COMMENT '升级后级别',
    `escalate_channel_id` BIGINT COMMENT '升级后使用的渠道ID',
    `is_active`           TINYINT(1) DEFAULT 1,
    `create_time`         DATETIME   DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='告警升级规则';


-- 11. 批处理性能日志表
CREATE TABLE IF NOT EXISTS `batch_performance_log`
(
    `id`                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    `job_name`           VARCHAR(100) NOT NULL COMMENT 'Job名称',
    `execution_id`       BIGINT       NOT NULL COMMENT 'Job执行ID',
    `step_name`          VARCHAR(100) COMMENT '步骤名称',
    `partition_id`       VARCHAR(50) COMMENT '分区ID',
    `status`             VARCHAR(20)  NOT NULL COMMENT '状态: STARTED/COMPLETED/FAILED/STOPPED',
    `start_time`         DATETIME     NOT NULL COMMENT '开始时间',
    `end_time`           DATETIME COMMENT '结束时间',
    `duration_ms`        BIGINT COMMENT '执行时长(毫秒)',
    `read_count`         BIGINT   DEFAULT 0 COMMENT '读取记录数',
    `write_count`        BIGINT   DEFAULT 0 COMMENT '写入记录数',
    `skip_count`         BIGINT   DEFAULT 0 COMMENT '跳过记录数',
    `records_per_second` DOUBLE COMMENT '处理速率(条/秒)',
    `cache_strategy`     VARCHAR(20) COMMENT '缓存策略: FILE/REDIS/ES/TEMP_TABLE',
    `cache_key`          VARCHAR(200) COMMENT '缓存键',
    `data_source_ids`    VARCHAR(500) COMMENT '涉及的数据源ID列表',
    `error_message`      TEXT COMMENT '错误信息',
    `extra_info`         TEXT COMMENT '额外信息JSON',
    `create_time`        DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_job_name` (`job_name`),
    INDEX `idx_execution_id` (`execution_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_start_time` (`start_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='批处理性能日志';

-- 12. 中间缓存元数据表
CREATE TABLE IF NOT EXISTS `batch_cache_metadata`
(
    `id`           BIGINT AUTO_INCREMENT PRIMARY KEY,
    `cache_key`    VARCHAR(200) NOT NULL UNIQUE COMMENT '缓存键',
    `cache_type`   VARCHAR(20)  NOT NULL COMMENT '缓存类型: FILE/REDIS/ES/TEMP_TABLE',
    `record_count` BIGINT     DEFAULT 0 COMMENT '记录数',
    `size_bytes`   BIGINT     DEFAULT 0 COMMENT '数据大小(字节)',
    `schema_info`  TEXT COMMENT '数据结构JSON',
    `source_info`  TEXT COMMENT '数据来源信息JSON',
    `created_by`   VARCHAR(100) COMMENT '创建者(Job名称)',
    `execution_id` BIGINT COMMENT '关联执行ID',
    `expire_time`  DATETIME COMMENT '过期时间',
    `is_expired`   TINYINT(1) DEFAULT 0 COMMENT '是否已过期',
    `create_time`  DATETIME   DEFAULT CURRENT_TIMESTAMP,
    `update_time`  DATETIME   DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_cache_type` (`cache_type`),
    INDEX `idx_expire_time` (`expire_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='批处理缓存元数据';


-- 工作流相关表结构
-- 执行时间: 2025-12-18

-- 1. 工作流主表
CREATE TABLE IF NOT EXISTS `monitor_workflow`
(
    `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name`            VARCHAR(100) NOT NULL COMMENT '工作流名称',
    `description`     TEXT COMMENT '描述',
    `cron_expression` VARCHAR(50) COMMENT '定时执行表达式',
    `is_active`       TINYINT(1) DEFAULT 1 COMMENT '是否启用: 1-启用, 0-停用',
    `timeout_seconds` INT        DEFAULT 3600 COMMENT '超时时间(秒)',
    `output_table`    VARCHAR(100) COMMENT '结果输出表名(自动生成或自定义)',
    `index_fields`    VARCHAR(500) COMMENT '索引字段,逗号分隔',
    `create_time`     DATETIME   DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME   DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='工作流配置';

-- 2. 工作流步骤表
CREATE TABLE IF NOT EXISTS `monitor_workflow_step`
(
    `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
    `workflow_id`     BIGINT       NOT NULL COMMENT '所属工作流ID',
    `step_order`      INT          NOT NULL COMMENT '执行顺序',
    `name`            VARCHAR(100) NOT NULL COMMENT '步骤名称',
    `step_type`       VARCHAR(20) DEFAULT 'SQL' COMMENT '步骤类型: SQL/TASK_REF/CONSTANT/LOOP',
    `datasource_id`   BIGINT COMMENT '数据源ID',
    `sql_script`      TEXT COMMENT 'SQL脚本',
    `result_variable` VARCHAR(50) COMMENT '结果变量名，供后续步骤引用',
    `config`          JSON COMMENT '其他配置(条件表达式等)',
    `position_x`      INT         DEFAULT 0 COMMENT '画布X坐标',
    `position_y`      INT         DEFAULT 0 COMMENT '画布Y坐标',
    `batch_enabled`   TINYINT(1)  DEFAULT 0 COMMENT '是否启用批处理: 0-否, 1-是',
    `id_column`       VARCHAR(50) COMMENT '分区主键列名(留空自动识别)',
    `partition_count` INT         DEFAULT 10 COMMENT '分区数量',
    `chunk_size`      INT         DEFAULT 1000 COMMENT 'Chunk大小',
    `cache_strategy`  VARCHAR(20) DEFAULT 'FILE' COMMENT '缓存策略: FILE/REDIS/ES/TEMP_TABLE',
    INDEX `idx_workflow` (`workflow_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='工作流步骤配置';

-- 3. 工作流执行记录表
CREATE TABLE IF NOT EXISTS `monitor_workflow_execution`
(
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
    `workflow_id`   BIGINT NOT NULL COMMENT '工作流ID',
    `status`        VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态: PENDING/RUNNING/SUCCESS/FAILED/TIMEOUT',
    `start_time`    DATETIME COMMENT '开始时间',
    `end_time`      DATETIME COMMENT '结束时间',
    `step_results`  JSON COMMENT '各步骤执行结果摘要',
    `error_message` TEXT COMMENT '错误信息',
    INDEX `idx_workflow_time` (`workflow_id`, `start_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='工作流执行记录';

-- ==================== Spring Batch 元数据表 ====================
-- Spring Batch 框架需要的 Job 仓库表

-- 13. BATCH_JOB_INSTANCE - 作业实例表
CREATE TABLE IF NOT EXISTS BATCH_JOB_INSTANCE
(
    JOB_INSTANCE_ID BIGINT       NOT NULL PRIMARY KEY,
    VERSION         BIGINT,
    JOB_NAME        VARCHAR(100) NOT NULL,
    JOB_KEY         VARCHAR(32)  NOT NULL,
    CONSTRAINT JOB_INST_UN UNIQUE (JOB_NAME, JOB_KEY)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 14. BATCH_JOB_EXECUTION - 作业执行表
CREATE TABLE IF NOT EXISTS BATCH_JOB_EXECUTION
(
    JOB_EXECUTION_ID BIGINT      NOT NULL PRIMARY KEY,
    VERSION          BIGINT,
    JOB_INSTANCE_ID  BIGINT      NOT NULL,
    CREATE_TIME      DATETIME(6) NOT NULL,
    START_TIME       DATETIME(6) DEFAULT NULL,
    END_TIME         DATETIME(6) DEFAULT NULL,
    STATUS           VARCHAR(10),
    EXIT_CODE        VARCHAR(2500),
    EXIT_MESSAGE     VARCHAR(2500),
    LAST_UPDATED     DATETIME(6),
    CONSTRAINT JOB_INST_EXEC_FK FOREIGN KEY (JOB_INSTANCE_ID)
        REFERENCES BATCH_JOB_INSTANCE (JOB_INSTANCE_ID)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 15. BATCH_JOB_EXECUTION_PARAMS - 作业执行参数表
CREATE TABLE IF NOT EXISTS BATCH_JOB_EXECUTION_PARAMS
(
    JOB_EXECUTION_ID BIGINT       NOT NULL,
    PARAMETER_NAME   VARCHAR(100) NOT NULL,
    PARAMETER_TYPE   VARCHAR(100) NOT NULL,
    PARAMETER_VALUE  VARCHAR(2500),
    IDENTIFYING      CHAR(1)      NOT NULL,
    CONSTRAINT JOB_EXEC_PARAMS_FK FOREIGN KEY (JOB_EXECUTION_ID)
        REFERENCES BATCH_JOB_EXECUTION (JOB_EXECUTION_ID)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 16. BATCH_STEP_EXECUTION - 步骤执行表
CREATE TABLE IF NOT EXISTS BATCH_STEP_EXECUTION
(
    STEP_EXECUTION_ID  BIGINT       NOT NULL PRIMARY KEY,
    VERSION            BIGINT       NOT NULL,
    STEP_NAME          VARCHAR(100) NOT NULL,
    JOB_EXECUTION_ID   BIGINT       NOT NULL,
    CREATE_TIME        DATETIME(6)  NOT NULL,
    START_TIME         DATETIME(6) DEFAULT NULL,
    END_TIME           DATETIME(6) DEFAULT NULL,
    STATUS             VARCHAR(10),
    COMMIT_COUNT       BIGINT,
    READ_COUNT         BIGINT,
    FILTER_COUNT       BIGINT,
    WRITE_COUNT        BIGINT,
    READ_SKIP_COUNT    BIGINT,
    WRITE_SKIP_COUNT   BIGINT,
    PROCESS_SKIP_COUNT BIGINT,
    ROLLBACK_COUNT     BIGINT,
    EXIT_CODE          VARCHAR(2500),
    EXIT_MESSAGE       VARCHAR(2500),
    LAST_UPDATED       DATETIME(6),
    CONSTRAINT JOB_EXEC_STEP_FK FOREIGN KEY (JOB_EXECUTION_ID)
        REFERENCES BATCH_JOB_EXECUTION (JOB_EXECUTION_ID)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 17. BATCH_STEP_EXECUTION_CONTEXT - 步骤执行上下文表
CREATE TABLE IF NOT EXISTS BATCH_STEP_EXECUTION_CONTEXT
(
    STEP_EXECUTION_ID  BIGINT        NOT NULL PRIMARY KEY,
    SHORT_CONTEXT      VARCHAR(2500) NOT NULL,
    SERIALIZED_CONTEXT TEXT,
    CONSTRAINT STEP_EXEC_CTX_FK FOREIGN KEY (STEP_EXECUTION_ID)
        REFERENCES BATCH_STEP_EXECUTION (STEP_EXECUTION_ID)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 18. BATCH_JOB_EXECUTION_CONTEXT - 作业执行上下文表
CREATE TABLE IF NOT EXISTS BATCH_JOB_EXECUTION_CONTEXT
(
    JOB_EXECUTION_ID   BIGINT        NOT NULL PRIMARY KEY,
    SHORT_CONTEXT      VARCHAR(2500) NOT NULL,
    SERIALIZED_CONTEXT TEXT,
    CONSTRAINT JOB_EXEC_CTX_FK FOREIGN KEY (JOB_EXECUTION_ID)
        REFERENCES BATCH_JOB_EXECUTION (JOB_EXECUTION_ID)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 19. BATCH_JOB_SEQ - 作业序列表
CREATE TABLE IF NOT EXISTS BATCH_JOB_SEQ
(
    ID         BIGINT  NOT NULL,
    UNIQUE_KEY CHAR(1) NOT NULL,
    CONSTRAINT UNIQUE_KEY_UN UNIQUE (UNIQUE_KEY)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

INSERT INTO BATCH_JOB_SEQ (ID, UNIQUE_KEY)
SELECT 0, '0'
FROM DUAL
WHERE NOT EXISTS (SELECT * FROM BATCH_JOB_SEQ);

-- 20. BATCH_JOB_EXECUTION_SEQ - 作业执行序列表
CREATE TABLE IF NOT EXISTS BATCH_JOB_EXECUTION_SEQ
(
    ID         BIGINT  NOT NULL,
    UNIQUE_KEY CHAR(1) NOT NULL,
    CONSTRAINT UNIQUE_KEY_UN2 UNIQUE (UNIQUE_KEY)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

INSERT INTO BATCH_JOB_EXECUTION_SEQ (ID, UNIQUE_KEY)
SELECT 0, '0'
FROM DUAL
WHERE NOT EXISTS (SELECT * FROM BATCH_JOB_EXECUTION_SEQ);

-- 21. BATCH_STEP_EXECUTION_SEQ - 步骤执行序列表
CREATE TABLE IF NOT EXISTS BATCH_STEP_EXECUTION_SEQ
(
    ID         BIGINT  NOT NULL,
    UNIQUE_KEY CHAR(1) NOT NULL,
    CONSTRAINT UNIQUE_KEY_UN3 UNIQUE (UNIQUE_KEY)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

INSERT INTO BATCH_STEP_EXECUTION_SEQ (ID, UNIQUE_KEY)
SELECT 0, '0'
FROM DUAL
WHERE NOT EXISTS (SELECT * FROM BATCH_STEP_EXECUTION_SEQ);
