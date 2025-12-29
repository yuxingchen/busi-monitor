-- 插入默认管理员账户 (密码: admin123)
INSERT IGNORE INTO `sys_user` (`username`, `password_hash`, `role`, `enabled`)
VALUES ('admin', '$2a$10$wIp9iDdYOjyPftT1H1X2pu9jde6aVKxP7UAg.DZaQu5IcMbAXdXRe', 'ADMIN', 1);

-- -- 插入预置监控模板
INSERT INTO `busi_monitor`.`monitor_template` (`id`, `name`, `category`, `sub_category`, `collect_type`,
                                               `collect_script`, `param_schema`, `default_threshold`, `default_cron`,
                                               `alarm_template_id`, `description`, `is_system`, `is_active`,
                                               `sort_order`, `create_time`, `update_time`)
VALUES (1, 'CPU使用率', 'BASIC', 'CPU', 'SSH_SCRIPT', 'top -bn1 | grep \"Cpu(s)\" | awk \'{print $2}\' | cut -d. -f1',
        NULL, '{\"operator\":\">\",\"value\":50}', '0 */5 * * * ?', 1, 'CPU使用率监控', 1, 1, 0, now(),now());
INSERT INTO `busi_monitor`.`monitor_template` (`id`, `name`, `category`, `sub_category`, `collect_type`,
                                               `collect_script`, `param_schema`, `default_threshold`, `default_cron`,
                                               `alarm_template_id`, `description`, `is_system`, `is_active`,
                                               `sort_order`, `create_time`, `update_time`)
VALUES (2, '内存使用率', 'BASIC', 'MEMORY', 'SSH_SCRIPT', 'free | grep Mem | awk \'{printf(\"%.0f\", $3/$2*100)}\'',
        NULL, '{\"operator\":\">\",\"value\":70}', '0 */5 * * * ?', 2, '内存使用率监控', 1, 1, 0, now(),now());
INSERT INTO `busi_monitor`.`monitor_template` (`id`, `name`, `category`, `sub_category`, `collect_type`,
                                               `collect_script`, `param_schema`, `default_threshold`, `default_cron`,
                                               `alarm_template_id`, `description`, `is_system`, `is_active`,
                                               `sort_order`, `create_time`, `update_time`)
VALUES (3, '磁盘使用率', 'BASIC', 'DISK', 'SSH_SCRIPT', 'df -h ${path} | tail -1 | awk \'{print $5}\' | tr -d \'%\'',
        NULL, '{\"operator\":\">\",\"value\":70}', '0 */5 * * * ?', 3, '磁盘使用率监控', 1, 1, 0, now(),now());
INSERT INTO `busi_monitor`.`monitor_template` (`id`, `name`, `category`, `sub_category`, `collect_type`,
                                               `collect_script`, `param_schema`, `default_threshold`, `default_cron`,
                                               `alarm_template_id`, `description`, `is_system`, `is_active`,
                                               `sort_order`, `create_time`, `update_time`)
VALUES (4, '网络连通性', 'BASIC', 'NETWORK', 'SSH_SCRIPT', 'ping -c 3 ${target} > /dev/null 2>&1 && echo 1 || echo 0',
        NULL, '{\"operator\":\"=\",\"value\":0}', '0 */5 * * * ?', 4, '网络连通性检测', 1, 1, 0, now(),now());
INSERT INTO `busi_monitor`.`monitor_template` (`id`, `name`, `category`, `sub_category`, `collect_type`,
                                               `collect_script`, `param_schema`, `default_threshold`, `default_cron`,
                                               `alarm_template_id`, `description`, `is_system`, `is_active`,
                                               `sort_order`, `create_time`, `update_time`)
VALUES (5, 'TCP连接数', 'BASIC', 'NETWORK', 'SSH_SCRIPT', 'netstat -an | grep ESTABLISHED | wc -l', NULL,
        '{\"operator\":\">\",\"value\":7000}', '0 */5 * * * ?', 5, 'TCP连接数监控', 1, 1, 0, now(),now());
INSERT INTO `busi_monitor`.`monitor_template` (`id`, `name`, `category`, `sub_category`, `collect_type`,
                                               `collect_script`, `param_schema`, `default_threshold`, `default_cron`,
                                               `alarm_template_id`, `description`, `is_system`, `is_active`,
                                               `sort_order`, `create_time`, `update_time`)
VALUES (6, '端口探测', 'COMPONENT', 'PORT', 'SSH_SCRIPT',
        'nc -v -w 3 -z ${target_ip} ${target_port} > /dev/null 2>&1 && echo 1 || echo 0', NULL,
        '{\"operator\":\"=\",\"value\":0}', '0 */5 * * * ?', 6, '端口探测', 1, 1, 0, now(),now());
INSERT INTO `busi_monitor`.`monitor_template` (`id`, `name`, `category`, `sub_category`, `collect_type`,
                                               `collect_script`, `param_schema`, `default_threshold`, `default_cron`,
                                               `alarm_template_id`, `description`, `is_system`, `is_active`,
                                               `sort_order`, `create_time`, `update_time`)
VALUES (7, 'HTTP可用性', 'APPLICATION', 'HTTP', 'SSH_SCRIPT', 'curl -o /dev/null -s -w \"%{http_code}\" ${url}', NULL,
        '{\"operator\":\"!=\",\"value\":200}', '0 */5 * * * ?', 7, 'HTTP可用性检测', 1, 1, 0, now(),now());
INSERT INTO `busi_monitor`.`monitor_template` (`id`, `name`, `category`, `sub_category`, `collect_type`,
                                               `collect_script`, `param_schema`, `default_threshold`, `default_cron`,
                                               `alarm_template_id`, `description`, `is_system`, `is_active`,
                                               `sort_order`, `create_time`, `update_time`)
VALUES (8, '应用日志错误', 'APPLICATION', 'LOG', 'SSH_SCRIPT', 'tail -100 ${log_path} | grep -c ERROR || echo 0', NULL,
        '{\"operator\":\">\",\"value\":0}', '0 */5 * * * ?', 8, '应用日志错误检测', 1, 1, 0, now(),now());
INSERT INTO `busi_monitor`.`monitor_template` (`id`, `name`, `category`, `sub_category`, `collect_type`,
                                               `collect_script`, `param_schema`, `default_threshold`, `default_cron`,
                                               `alarm_template_id`, `description`, `is_system`, `is_active`,
                                               `sort_order`, `create_time`, `update_time`)
VALUES (10, 'MySQL端口', 'COMPONENT', 'MySQL', 'SSH_SCRIPT',
        'nc -v -w 3 -z 127.0.0.1 3306 > /dev/null 2>&1 && echo 1 || echo 0',
        '{\"port\":{\"type\":\"number\",\"default\":3306}}', '{\"operator\":\"=\",\"value\":0}', '0 */5 * * * ?', 9,
        'MySQL默认端口3306监控', 1, 1, 10, now(),now());
INSERT INTO `busi_monitor`.`monitor_template` (`id`, `name`, `category`, `sub_category`, `collect_type`,
                                               `collect_script`, `param_schema`, `default_threshold`, `default_cron`,
                                               `alarm_template_id`, `description`, `is_system`, `is_active`,
                                               `sort_order`, `create_time`, `update_time`)
VALUES (11, 'Redis端口', 'COMPONENT', 'Redis', 'SSH_SCRIPT',
        'nc -v -w 3 -z 127.0.0.1 6379 > /dev/null 2>&1 && echo 1 || echo 0',
        '{\"port\":{\"type\":\"number\",\"default\":6379}}', '{\"operator\":\"=\",\"value\":0}', '0 */5 * * * ?', 10,
        'Redis默认端口6379监控', 1, 1, 11, now(),now());
INSERT INTO `busi_monitor`.`monitor_template` (`id`, `name`, `category`, `sub_category`, `collect_type`,
                                               `collect_script`, `param_schema`, `default_threshold`, `default_cron`,
                                               `alarm_template_id`, `description`, `is_system`, `is_active`,
                                               `sort_order`, `create_time`, `update_time`)
VALUES (12, 'Nginx端口', 'COMPONENT', 'Nginx', 'SSH_SCRIPT',
        'nc -v -w 3 -z 127.0.0.1 80 > /dev/null 2>&1 && echo 1 || echo 0',
        '{\"port\":{\"type\":\"number\",\"default\":80}}', '{\"operator\":\"=\",\"value\":0}', '0 */5 * * * ?', 11,
        'Nginx默认端口80监控', 1, 1, 12, now(),now());
INSERT INTO `busi_monitor`.`monitor_template` (`id`, `name`, `category`, `sub_category`, `collect_type`,
                                               `collect_script`, `param_schema`, `default_threshold`, `default_cron`,
                                               `alarm_template_id`, `description`, `is_system`, `is_active`,
                                               `sort_order`, `create_time`, `update_time`)
VALUES (13, 'MongoDB端口', 'COMPONENT', 'MongoDB', 'SSH_SCRIPT',
        'nc -v -w 3 -z 127.0.0.1 27017 > /dev/null 2>&1 && echo 1 || echo 0',
        '{\"port\":{\"type\":\"number\",\"default\":27017}}', '{\"operator\":\"=\",\"value\":0}', '0 */5 * * * ?', 12,
        'MongoDB默认端口27017监控', 1, 1, 13, now(),now());
INSERT INTO `busi_monitor`.`monitor_template` (`id`, `name`, `category`, `sub_category`, `collect_type`,
                                               `collect_script`, `param_schema`, `default_threshold`, `default_cron`,
                                               `alarm_template_id`, `description`, `is_system`, `is_active`,
                                               `sort_order`, `create_time`, `update_time`)
VALUES (14, 'Kafka端口', 'COMPONENT', 'Kafka', 'SSH_SCRIPT',
        'nc -v -w 3 -z 127.0.0.1 9092 > /dev/null 2>&1 && echo 1 || echo 0',
        '{\"port\":{\"type\":\"number\",\"default\":9092}}', '{\"operator\":\"=\",\"value\":0}', '0 */5 * * * ?', 13,
        'Kafka默认端口9092监控', 1, 1, 14, now(),now());
INSERT INTO `busi_monitor`.`monitor_template` (`id`, `name`, `category`, `sub_category`, `collect_type`,
                                               `collect_script`, `param_schema`, `default_threshold`, `default_cron`,
                                               `alarm_template_id`, `description`, `is_system`, `is_active`,
                                               `sort_order`, `create_time`, `update_time`)
VALUES (15, 'Elasticsearch端口', 'COMPONENT', 'ES', 'SSH_SCRIPT',
        'nc -v -w 3 -z 127.0.0.1 9200 > /dev/null 2>&1 && echo 1 || echo 0',
        '{\"port\":{\"type\":\"number\",\"default\":9200}}', '{\"operator\":\"=\",\"value\":0}', '0 */5 * * * ?', 14,
        'Elasticsearch默认端口9200监控', 1, 1, 15, now(),now());
INSERT INTO `busi_monitor`.`monitor_template` (`id`, `name`, `category`, `sub_category`, `collect_type`,
                                               `collect_script`, `param_schema`, `default_threshold`, `default_cron`,
                                               `alarm_template_id`, `description`, `is_system`, `is_active`,
                                               `sort_order`, `create_time`, `update_time`)
VALUES (16, 'Zookeeper端口', 'COMPONENT', 'Zookeeper', 'SSH_SCRIPT',
        'nc -v -w 3 -z 127.0.0.1 2181 > /dev/null 2>&1 && echo 1 || echo 0',
        '{\"port\":{\"type\":\"number\",\"default\":2181}}', '{\"operator\":\"=\",\"value\":0}', '0 */5 * * * ?', 15,
        'Zookeeper默认端口2181监控', 1, 1, 16, now(),now());
INSERT INTO `busi_monitor`.`monitor_template` (`id`, `name`, `category`, `sub_category`, `collect_type`,
                                               `collect_script`, `param_schema`, `default_threshold`, `default_cron`,
                                               `alarm_template_id`, `description`, `is_system`, `is_active`,
                                               `sort_order`, `create_time`, `update_time`)
VALUES (17, 'RabbitMQ端口', 'COMPONENT', 'RabbitMQ', 'SSH_SCRIPT',
        'nc -v -w 3 -z 127.0.0.1 5672 > /dev/null 2>&1 && echo 1 || echo 0',
        '{\"port\":{\"type\":\"number\",\"default\":5672}}', '{\"operator\":\"=\",\"value\":0}', '0 */5 * * * ?', 16,
        'RabbitMQ默认端口5672监控', 1, 1, 17, now(),now());
INSERT INTO `busi_monitor`.`monitor_template` (`id`, `name`, `category`, `sub_category`, `collect_type`,
                                               `collect_script`, `param_schema`, `default_threshold`, `default_cron`,
                                               `alarm_template_id`, `description`, `is_system`, `is_active`,
                                               `sort_order`, `create_time`, `update_time`)
VALUES (18, 'MinIO端口', 'COMPONENT', 'MinIO', 'SSH_SCRIPT',
        'nc -v -w 3 -z 127.0.0.1 9000 > /dev/null 2>&1 && echo 1 || echo 0',
        '{\"port\":{\"type\":\"number\",\"default\":9000}}', '{\"operator\":\"=\",\"value\":0}', '0 */5 * * * ?', 17,
        'MinIO默认端口9000监控', 1, 1, 18, now(),now());
INSERT INTO `busi_monitor`.`monitor_template` (`id`, `name`, `category`, `sub_category`, `collect_type`,
                                               `collect_script`, `param_schema`, `default_threshold`, `default_cron`,
                                               `alarm_template_id`, `description`, `is_system`, `is_active`,
                                               `sort_order`, `create_time`, `update_time`)
VALUES (19, 'Hadoop端口', 'COMPONENT', 'Hadoop', 'SSH_SCRIPT',
        'nc -v -w 3 -z 127.0.0.1 9870 > /dev/null 2>&1 && echo 1 || echo 0',
        '{\"port\":{\"type\":\"number\",\"default\":9870}}', '{\"operator\":\"=\",\"value\":0}', '0 */5 * * * ?', 18,
        'Hadoop NameNode默认端口9870监控', 1, 1, 19, now(),now());
INSERT INTO `busi_monitor`.`monitor_template` (`id`, `name`, `category`, `sub_category`, `collect_type`,
                                               `collect_script`, `param_schema`, `default_threshold`, `default_cron`,
                                               `alarm_template_id`, `description`, `is_system`, `is_active`,
                                               `sort_order`, `create_time`, `update_time`)
VALUES (20, 'FTP端口', 'COMPONENT', 'FTP', 'SSH_SCRIPT',
        'nc -v -w 3 -z 127.0.0.1 21 > /dev/null 2>&1 && echo 1 || echo 0',
        '{\"port\":{\"type\":\"number\",\"default\":21}}', '{\"operator\":\"=\",\"value\":0}', '0 */5 * * * ?', 19,
        'FTP默认端口21监控', 1, 1, 20, now(),now());
-- 监控模板
INSERT INTO `busi_monitor`.`alarm_template` (`id`, `name`, `subject`, `content`, `content_type`, `is_default`, `create_time`, `update_time`) VALUES (1, 'CPU使用率告警', 'CPU使用率告警', '${serverName}(${ip}) CPU使用率${value}%，超过阈值${threshold}%', 'TEXT', 0, now(),now());
INSERT INTO `busi_monitor`.`alarm_template` (`id`, `name`, `subject`, `content`, `content_type`, `is_default`, `create_time`, `update_time`) VALUES (2, '内存使用率告警', '内存使用率告警', '${serverName}(${ip}) 内存使用率${value}%，超过阈值${threshold}%', 'TEXT', 0, now(),now());
INSERT INTO `busi_monitor`.`alarm_template` (`id`, `name`, `subject`, `content`, `content_type`, `is_default`, `create_time`, `update_time`) VALUES (3, '磁盘使用率告警', '磁盘使用率告警', '${serverName}(${ip}) 磁盘${path}使用率${value}%，超过阈值${threshold}%', 'TEXT', 0, now(),now());
INSERT INTO `busi_monitor`.`alarm_template` (`id`, `name`, `subject`, `content`, `content_type`, `is_default`, `create_time`, `update_time`) VALUES (4, '网络连通性告警', '网络连通性告警', '${serverName}(${ip}) 到${target}网络不通', 'TEXT', 0, now(),now());
INSERT INTO `busi_monitor`.`alarm_template` (`id`, `name`, `subject`, `content`, `content_type`, `is_default`, `create_time`, `update_time`) VALUES (5, 'TCP连接数告警', 'TCP连接数告警', '${serverName}(${ip}) TCP连接数${value}，超过阈值${threshold}', 'TEXT', 0, now(),now());
INSERT INTO `busi_monitor`.`alarm_template` (`id`, `name`, `subject`, `content`, `content_type`, `is_default`, `create_time`, `update_time`) VALUES (6, '端口探测告警', '端口探测告警', '${serverName}(${ip}) 端口${target_ip}:${target_port}不可达', 'TEXT', 0, now(),now());
INSERT INTO `busi_monitor`.`alarm_template` (`id`, `name`, `subject`, `content`, `content_type`, `is_default`, `create_time`, `update_time`) VALUES (7, 'HTTP可用性告警', 'HTTP可用性告警', '${serverName}(${ip}) HTTP请求${url}返回${value}，非200', 'TEXT', 0, now(),now());
INSERT INTO `busi_monitor`.`alarm_template` (`id`, `name`, `subject`, `content`, `content_type`, `is_default`, `create_time`, `update_time`) VALUES (8, '应用日志错误告警', '应用日志错误告警', '${serverName}(${ip}) 日志${log_path}发现${value}个ERROR', 'TEXT', 0, now(),now());
INSERT INTO `busi_monitor`.`alarm_template` (`id`, `name`, `subject`, `content`, `content_type`, `is_default`, `create_time`, `update_time`) VALUES (9, 'MySQL端口告警', 'MySQL端口告警', '${serverName}(${ip}) MySQL端口3306不可达', 'TEXT', 0, now(),now());
INSERT INTO `busi_monitor`.`alarm_template` (`id`, `name`, `subject`, `content`, `content_type`, `is_default`, `create_time`, `update_time`) VALUES (10, 'Redis端口告警', 'Redis端口告警', '${serverName}(${ip}) Redis端口6379不可达', 'TEXT', 0, now(),now());
INSERT INTO `busi_monitor`.`alarm_template` (`id`, `name`, `subject`, `content`, `content_type`, `is_default`, `create_time`, `update_time`) VALUES (11, 'Nginx端口告警', 'Nginx端口告警', '${serverName}(${ip}) Nginx端口80不可达', 'TEXT', 0, now(),now());
INSERT INTO `busi_monitor`.`alarm_template` (`id`, `name`, `subject`, `content`, `content_type`, `is_default`, `create_time`, `update_time`) VALUES (12, 'MongoDB端口告警', 'MongoDB端口告警', '${serverName}(${ip}) MongoDB端口27017不可达', 'TEXT', 0, now(),now());
INSERT INTO `busi_monitor`.`alarm_template` (`id`, `name`, `subject`, `content`, `content_type`, `is_default`, `create_time`, `update_time`) VALUES (13, 'Kafka端口告警', 'Kafka端口告警', '${serverName}(${ip}) Kafka端口9092不可达', 'TEXT', 0, now(),now());
INSERT INTO `busi_monitor`.`alarm_template` (`id`, `name`, `subject`, `content`, `content_type`, `is_default`, `create_time`, `update_time`) VALUES (14, 'Elasticsearch端口告警', 'Elasticsearch端口告警', '${serverName}(${ip}) ES端口9200不可达', 'TEXT', 0, now(),now());
INSERT INTO `busi_monitor`.`alarm_template` (`id`, `name`, `subject`, `content`, `content_type`, `is_default`, `create_time`, `update_time`) VALUES (15, 'Zookeeper端口告警', 'Zookeeper端口告警', '${serverName}(${ip}) Zookeeper端口2181不可达', 'TEXT', 0, now(),now());
INSERT INTO `busi_monitor`.`alarm_template` (`id`, `name`, `subject`, `content`, `content_type`, `is_default`, `create_time`, `update_time`) VALUES (16, 'RabbitMQ端口告警', 'RabbitMQ端口告警', '${serverName}(${ip}) RabbitMQ端口5672不可达', 'TEXT', 0, now(),now());
INSERT INTO `busi_monitor`.`alarm_template` (`id`, `name`, `subject`, `content`, `content_type`, `is_default`, `create_time`, `update_time`) VALUES (17, 'MinIO端口告警', 'MinIO端口告警', '${serverName}(${ip}) MinIO端口9000不可达', 'TEXT', 0, now(),now());
INSERT INTO `busi_monitor`.`alarm_template` (`id`, `name`, `subject`, `content`, `content_type`, `is_default`, `create_time`, `update_time`) VALUES (18, 'Hadoop端口告警', 'Hadoop端口告警', '${serverName}(${ip}) Hadoop端口9870不可达', 'TEXT', 0, now(),now());
INSERT INTO `busi_monitor`.`alarm_template` (`id`, `name`, `subject`, `content`, `content_type`, `is_default`, `create_time`, `update_time`) VALUES (19, 'FTP端口告警', 'FTP端口告警', '${serverName}(${ip}) FTP端口21不可达', 'TEXT', 0, now(),now());
INSERT INTO `busi_monitor`.`alarm_template` (`id`, `name`, `subject`, `content`, `content_type`, `is_default`, `create_time`, `update_time`) VALUES (20, '磁盘使用率告警.md', '磁盘使用率告警', '> 服务名：**${serverName}**\n> IP：**${ip}**\n> 内容：磁盘${path}使用率${value}%，超过阈值***${threshold}%***', 'MARKDOWN', 0, now(),now());