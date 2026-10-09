-- =====================================================================
-- Realm DB Schema v1.0.0 · CN Edition (MySQL 5.7)
-- =====================================================================
SET NAMES utf8mb4;

-- =====================================================================
-- IAM 身份权限模块
-- =====================================================================

DROP TABLE IF EXISTS `iam_user`;
CREATE TABLE `iam_user`
(
    `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID（用户ID）',
    `username`        varchar(30)  NOT NULL COMMENT '用户账号：租户内唯一',
    `password_hash`   varchar(255) NOT NULL COMMENT '密码哈希',
    `nickname`        varchar(64)  NOT NULL COMMENT '用户昵称',
    `real_name`       varchar(64)           DEFAULT NULL COMMENT '真实姓名',
    `description`     varchar(500)          DEFAULT NULL COMMENT '描述',
    `department_id`   bigint                DEFAULT NULL COMMENT '部门ID：关联 iam_department.id',
    `email`           varchar(254)          DEFAULT NULL COMMENT '用户邮箱',
    `phone_number`    varchar(20)           DEFAULT NULL COMMENT '手机号码',
    `gender`          tinyint      NOT NULL DEFAULT 0 COMMENT '性别：0-未知；1-男；2-女',
    `avatar_url`      varchar(512)          DEFAULT NULL COMMENT '头像地址',
    `status`          tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0-正常；1-停用',
    `last_login_ip`   varchar(45)           DEFAULT NULL COMMENT '最后登录 IP',
    `last_login_time` datetime              DEFAULT NULL COMMENT '最后登录时间',

    `tenant_id`       bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `is_deleted`      tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
    `version`         int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`       bigint                DEFAULT NULL COMMENT '创建人ID',
    `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       bigint                DEFAULT NULL COMMENT '更新人ID',
    `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    `active_username` varchar(30)
                      GENERATED ALWAYS AS (IF(`is_deleted` = 0, `username`, NULL)) STORED
        COMMENT '有效用户名；逻辑删除下唯一约束辅助列，is_deleted=0 时为 username，否则为 NULL',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_iam_user_tenant_active_username` (`tenant_id`, `active_username`) COMMENT '租户内有效用户名唯一',
    INDEX `idx_iam_user_tenant_phone` (`tenant_id`, `phone_number`) COMMENT '手机号查询',
    INDEX `idx_iam_user_tenant_email` (`tenant_id`, `email`) COMMENT '邮箱查询',
    INDEX `idx_iam_user_tenant_department` (`tenant_id`, `department_id`) COMMENT '部门查询',
    INDEX `idx_iam_user_tenant_status` (`tenant_id`, `status`) COMMENT '状态查询',
    INDEX `idx_iam_user_tenant_deleted` (`tenant_id`, `is_deleted`) COMMENT '逻辑删除查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='IAM-用户表';

DROP TABLE IF EXISTS `iam_role`;
CREATE TABLE `iam_role`
(
    `id`               bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID（角色ID）',
    `role_name`        varchar(128) NOT NULL COMMENT '角色名称',
    `role_code`        varchar(100) NOT NULL COMMENT '角色编码：租户内唯一',
    `sort_order`       int          NOT NULL DEFAULT 0 COMMENT '显示顺序',
    `data_scope`       tinyint      NOT NULL DEFAULT 5 COMMENT '数据范围：1-全部；2-自定义；3-本部门；4-本部门及以下；5-仅本人',
    `status`           tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0-正常；1-停用',
    `description`      varchar(500)          DEFAULT NULL COMMENT '描述',

    `tenant_id`        bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `is_deleted`       tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
    `version`          int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`        bigint                DEFAULT NULL COMMENT '创建人ID',
    `create_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        bigint                DEFAULT NULL COMMENT '更新人ID',
    `update_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    `active_role_code` varchar(100)
                       GENERATED ALWAYS AS (IF(`is_deleted` = 0, `role_code`, NULL)) STORED
        COMMENT '有效角色编码；逻辑删除下唯一约束辅助列，is_deleted=0 时为 role_code，否则为 NULL',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_iam_role_tenant_active_role_code` (`tenant_id`, `active_role_code`) COMMENT '租户内有效角色编码唯一',
    INDEX `idx_iam_role_tenant_status` (`tenant_id`, `status`) COMMENT '状态查询',
    INDEX `idx_iam_role_tenant_deleted` (`tenant_id`, `is_deleted`) COMMENT '逻辑删除查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='IAM-角色表';

DROP TABLE IF EXISTS `iam_menu`;
CREATE TABLE `iam_menu`
(
    `id`                   bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID（菜单ID）',
    `menu_name`            varchar(128) NOT NULL COMMENT '菜单名称',
    `permission_code`      varchar(100)          DEFAULT NULL COMMENT '权限标识：如 system:user:query',
    `menu_type`            tinyint      NOT NULL COMMENT '菜单类型：1-目录；2-菜单；3-按钮',
    `sort_order`           int          NOT NULL DEFAULT 0 COMMENT '显示顺序',
    `parent_id`            bigint       NOT NULL DEFAULT 0 COMMENT '父级ID：0-顶级',
    `path`                 varchar(200)          DEFAULT NULL COMMENT '路由地址',
    `icon`                 varchar(100)          DEFAULT NULL COMMENT '菜单图标',
    `component`            varchar(255)          DEFAULT NULL COMMENT '前端组件路径',
    `component_name`       varchar(255)          DEFAULT NULL COMMENT '前端组件名',
    `status`               tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0-正常；1-停用',
    `is_visible`           tinyint      NOT NULL DEFAULT 1 COMMENT '是否可见：0-否；1-是',
    `is_keep_alive`        tinyint      NOT NULL DEFAULT 1 COMMENT '是否缓存：0-否；1-是',
    `is_always_show`       tinyint      NOT NULL DEFAULT 1 COMMENT '是否总是显示：0-否；1-是',

    `tenant_id`            bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `is_deleted`           tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
    `version`              int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`            bigint                DEFAULT NULL COMMENT '创建人ID',
    `create_time`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`            bigint                DEFAULT NULL COMMENT '更新人ID',
    `update_time`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    `active_permission_code` varchar(100)
        GENERATED ALWAYS AS (IF(`is_deleted` = 0, `permission_code`, NULL)) STORED
        COMMENT '有效权限标识；逻辑删除下唯一约束辅助列，is_deleted=0 时为 permission_code，否则为 NULL',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_iam_menu_tenant_active_permission_code` (`tenant_id`, `active_permission_code`) COMMENT '租户内有效权限标识唯一',
    INDEX `idx_iam_menu_tenant_parent` (`tenant_id`, `parent_id`) COMMENT '父级查询',
    INDEX `idx_iam_menu_tenant_type` (`tenant_id`, `menu_type`) COMMENT '类型查询',
    INDEX `idx_iam_menu_tenant_status` (`tenant_id`, `status`) COMMENT '状态查询',
    INDEX `idx_iam_menu_tenant_deleted` (`tenant_id`, `is_deleted`) COMMENT '逻辑删除查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='IAM-菜单权限表';

DROP TABLE IF EXISTS `iam_position`;
CREATE TABLE `iam_position`
(
    `id`                   bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID（岗位ID）',
    `position_code`        varchar(64)  NOT NULL COMMENT '岗位编码：租户内唯一',
    `position_name`        varchar(128) NOT NULL COMMENT '岗位名称',
    `sort_order`           int          NOT NULL DEFAULT 0 COMMENT '显示顺序',
    `status`               tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0-正常；1-停用',
    `description`          varchar(500)          DEFAULT NULL COMMENT '描述',

    `tenant_id`            bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `is_deleted`           tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
    `version`              int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`            bigint                DEFAULT NULL COMMENT '创建人ID',
    `create_time`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`            bigint                DEFAULT NULL COMMENT '更新人ID',
    `update_time`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    `active_position_code` varchar(64)
                           GENERATED ALWAYS AS (IF(`is_deleted` = 0, `position_code`, NULL)) STORED
        COMMENT '有效岗位编码；逻辑删除下唯一约束辅助列，is_deleted=0 时为 position_code，否则为 NULL',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_iam_position_tenant_active_position_code` (`tenant_id`, `active_position_code`) COMMENT '租户内有效岗位编码唯一',
    INDEX `idx_iam_position_tenant_status` (`tenant_id`, `status`) COMMENT '状态查询',
    INDEX `idx_iam_position_tenant_deleted` (`tenant_id`, `is_deleted`) COMMENT '逻辑删除查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='IAM-岗位表';

DROP TABLE IF EXISTS `iam_department`;
CREATE TABLE `iam_department`
(
    `id`               bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID（部门ID）',
    `department_name`  varchar(128) NOT NULL COMMENT '部门名称',
    `parent_id`        bigint       NOT NULL DEFAULT 0 COMMENT '父级ID：0-顶级',
    `sort_order`       int          NOT NULL DEFAULT 0 COMMENT '显示顺序',
    `manager_user_id`  bigint                DEFAULT NULL COMMENT '部门负责人用户ID：关联 iam_user.id',
    `phone_number`     varchar(20)           DEFAULT NULL COMMENT '联系电话',
    `email`            varchar(254)          DEFAULT NULL COMMENT '邮箱',
    `status`           tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0-正常；1-停用',

    `tenant_id`        bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `is_deleted`       tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
    `version`          int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`        bigint                DEFAULT NULL COMMENT '创建人ID',
    `create_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        bigint                DEFAULT NULL COMMENT '更新人ID',
    `update_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (`id`),
    INDEX `idx_iam_department_tenant_parent` (`tenant_id`, `parent_id`) COMMENT '父级查询',
    INDEX `idx_iam_department_tenant_manager` (`tenant_id`, `manager_user_id`) COMMENT '负责人查询',
    INDEX `idx_iam_department_tenant_status` (`tenant_id`, `status`) COMMENT '状态查询',
    INDEX `idx_iam_department_tenant_deleted` (`tenant_id`, `is_deleted`) COMMENT '逻辑删除查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='IAM-部门表';

DROP TABLE IF EXISTS `iam_user_role`;
CREATE TABLE `iam_user_role`
(
    `id`          bigint   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`     bigint   NOT NULL COMMENT '用户ID：关联 iam_user.id',
    `role_id`     bigint   NOT NULL COMMENT '角色ID：关联 iam_role.id',
    `tenant_id`   bigint   NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `create_by`   bigint            DEFAULT NULL COMMENT '创建人ID',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_iam_user_role_user_role_tenant` (`user_id`, `role_id`, `tenant_id`) COMMENT '用户角色租户唯一',
    INDEX `idx_iam_user_role_role` (`role_id`) COMMENT '角色查询',
    INDEX `idx_iam_user_role_tenant` (`tenant_id`) COMMENT '租户查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='IAM-用户角色关联表';

DROP TABLE IF EXISTS `iam_role_menu`;
CREATE TABLE `iam_role_menu`
(
    `id`          bigint   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `role_id`     bigint   NOT NULL COMMENT '角色ID：关联 iam_role.id',
    `menu_id`     bigint   NOT NULL COMMENT '菜单ID：关联 iam_menu.id',
    `tenant_id`   bigint   NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `create_by`   bigint            DEFAULT NULL COMMENT '创建人ID',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_iam_role_menu_role_menu_tenant` (`role_id`, `menu_id`, `tenant_id`) COMMENT '角色菜单租户唯一',
    INDEX `idx_iam_role_menu_menu` (`menu_id`) COMMENT '菜单查询',
    INDEX `idx_iam_role_menu_tenant` (`tenant_id`) COMMENT '租户查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='IAM-角色菜单关联表';

DROP TABLE IF EXISTS `iam_user_position`;
CREATE TABLE `iam_user_position`
(
    `id`          bigint   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`     bigint   NOT NULL COMMENT '用户ID：关联 iam_user.id',
    `position_id` bigint   NOT NULL COMMENT '岗位ID：关联 iam_position.id',
    `tenant_id`   bigint   NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `create_by`   bigint            DEFAULT NULL COMMENT '创建人ID',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_iam_user_position_user_position_tenant` (`user_id`, `position_id`, `tenant_id`) COMMENT '用户岗位租户唯一',
    INDEX `idx_iam_user_position_position` (`position_id`) COMMENT '岗位查询',
    INDEX `idx_iam_user_position_tenant` (`tenant_id`) COMMENT '租户查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='IAM-用户岗位关联表';

DROP TABLE IF EXISTS `iam_role_department`;
CREATE TABLE `iam_role_department`
(
    `id`            bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `role_id`       bigint NOT NULL COMMENT '角色ID：关联 iam_role.id',
    `department_id` bigint NOT NULL COMMENT '部门ID：关联 iam_department.id',
    `tenant_id`     bigint NOT NULL DEFAULT 0 COMMENT '租户ID',
    `create_by`     bigint          DEFAULT NULL COMMENT '创建人ID',
    `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_iam_role_dept_role_dept_tenant` (`role_id`, `department_id`, `tenant_id`) COMMENT '角色部门租户唯一',
    INDEX `idx_iam_role_dept_dept` (`department_id`) COMMENT '部门查询',
    INDEX `idx_iam_role_dept_tenant` (`tenant_id`) COMMENT '租户查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='IAM-角色与部门关联表';

-- =====================================================================
-- Infra 基础设施模块
-- =====================================================================

DROP TABLE IF EXISTS `infra_config`;
CREATE TABLE `infra_config`
(
    `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `category`          varchar(64)  NOT NULL COMMENT '参数分组：如 biz、url、system',
    `config_type`       tinyint      NOT NULL COMMENT '参数类型：1-系统内置；2-自定义',
    `config_name`       varchar(128) NOT NULL COMMENT '参数名称',
    `config_key`        varchar(100) NOT NULL COMMENT '参数键名：租户内唯一',
    `config_value`      varchar(500) NOT NULL COMMENT '参数键值',
    `is_visible`        tinyint      NOT NULL DEFAULT 1 COMMENT '是否可见：0-否；1-是',
    `description`       varchar(500)          DEFAULT NULL COMMENT '描述',

    `tenant_id`         bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `is_deleted`        tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
    `version`           int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`         bigint                DEFAULT NULL COMMENT '创建人ID',
    `create_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`         bigint                DEFAULT NULL COMMENT '更新人ID',
    `update_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    `active_config_key` varchar(100)
                        GENERATED ALWAYS AS (IF(`is_deleted` = 0, `config_key`, NULL)) STORED
        COMMENT '有效参数键名；逻辑删除下唯一约束辅助列，is_deleted=0 时为 config_key，否则为 NULL',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_infra_config_tenant_active_config_key` (`tenant_id`, `active_config_key`) COMMENT '租户内有效参数键名唯一',
    INDEX `idx_infra_config_tenant_category` (`tenant_id`, `category`) COMMENT '分组查询',
    INDEX `idx_infra_config_tenant_type` (`tenant_id`, `config_type`) COMMENT '类型查询',
    INDEX `idx_infra_config_tenant_deleted` (`tenant_id`, `is_deleted`) COMMENT '逻辑删除查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='基础设施-参数配置表';

DROP TABLE IF EXISTS `infra_job`;
CREATE TABLE `infra_job`
(
    `id`                  bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID（任务ID）',
    `job_name`            varchar(128) NOT NULL COMMENT '任务名称：租户内唯一',
    `job_status`          tinyint      NOT NULL DEFAULT 0 COMMENT '任务状态：0-初始化；1-运行中；2-暂停',
    `handler_name`        varchar(128) NOT NULL COMMENT '任务处理器名称',
    `handler_params`  varchar(512)          DEFAULT NULL COMMENT '任务处理器参数',
    `cron_expression`     varchar(64)  NOT NULL COMMENT 'CRON 表达式',
    `retry_count`         int          NOT NULL DEFAULT 0 COMMENT '重试次数：0-不重试',
    `retry_interval_ms`   int          NOT NULL DEFAULT 0 COMMENT '重试间隔（毫秒）',
    `monitor_timeout_ms`  int          NOT NULL DEFAULT 0 COMMENT '监控超时时间（毫秒）',

    `tenant_id`           bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `is_deleted`          tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
    `version`             int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`           bigint                DEFAULT NULL COMMENT '创建人ID',
    `create_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`           bigint                DEFAULT NULL COMMENT '更新人ID',
    `update_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    `active_job_name`     varchar(128)
                          GENERATED ALWAYS AS (IF(`is_deleted` = 0, `job_name`, NULL)) STORED
        COMMENT '有效任务名称；逻辑删除下唯一约束辅助列，is_deleted=0 时为 job_name，否则为 NULL',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_infra_job_tenant_active_job_name` (`tenant_id`, `active_job_name`) COMMENT '租户内有效任务名称唯一',
    INDEX `idx_infra_job_tenant_status` (`tenant_id`, `job_status`) COMMENT '状态查询',
    INDEX `idx_infra_job_tenant_handler` (`tenant_id`, `handler_name`) COMMENT '处理器查询',
    INDEX `idx_infra_job_tenant_deleted` (`tenant_id`, `is_deleted`) COMMENT '逻辑删除查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='基础设施-定时任务表';

DROP TABLE IF EXISTS `infra_job_log`;
CREATE TABLE `infra_job_log`
(
    `id`                  bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `job_id`              bigint       NOT NULL COMMENT '定时任务ID：关联 infra_job.id',
    `handler_name`        varchar(128) NOT NULL COMMENT '任务处理器名称（快照）',
    `handler_params`  varchar(512)          DEFAULT NULL COMMENT '任务处理器参数（快照）',
    `execution_index`     smallint      NOT NULL DEFAULT 1 COMMENT '第几次执行：从1开始',
    `start_time`          datetime     NOT NULL COMMENT '开始执行时间',
    `end_time`            datetime              DEFAULT NULL COMMENT '结束执行时间',
    `duration_ms`         int                   DEFAULT NULL COMMENT '执行时长（毫秒）',
    `execution_status`    tinyint      NOT NULL COMMENT '执行状态：0-运行中；1-成功；2-失败',
    `result_data`         text                  DEFAULT NULL COMMENT '结果数据',
    `tenant_id`           bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `create_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (`id`),
    INDEX `idx_infra_job_log_job_time` (`job_id`, `create_time`) COMMENT '任务日志查询',
    INDEX `idx_infra_job_log_status_time` (`execution_status`, `create_time`) COMMENT '状态查询',
    INDEX `idx_infra_job_log_tenant_time` (`tenant_id`, `create_time`) COMMENT '租户查询',
    INDEX `idx_infra_job_log_time` (`create_time`) COMMENT '时间查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='基础设施-定时任务执行日志表';

DROP TABLE IF EXISTS `infra_file_storage_config`;
CREATE TABLE `infra_file_storage_config`
(
    `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `config_name`       varchar(128) NOT NULL COMMENT '配置名称：租户内唯一',
    `storage_type`      tinyint      NOT NULL COMMENT '存储类型：1-数据库；10-本地磁盘；11-FTP；12-SFTP；20-S3对象存储',
    `encrypted_config`  varbinary(8192) NOT NULL COMMENT '加密后的存储配置（JSON 格式）',
    `is_default`        tinyint      NOT NULL DEFAULT 0 COMMENT '是否默认：0-否；1-是（租户内唯一）',
    `default_marker`    tinyint      GENERATED ALWAYS AS (
                            IF(`is_default` = 1 AND `is_deleted` = 0, 1, NULL)
                            ) STORED COMMENT '默认配置标记；is_default=1 且 is_deleted=0 时为 1，否则为 NULL，用于租户内默认配置唯一',
    `status`            tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0-启用；1-禁用',
    `description`       varchar(255)          DEFAULT NULL COMMENT '描述',

    `tenant_id`         bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `is_deleted`        tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
    `version`           int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`         bigint                DEFAULT NULL COMMENT '创建人ID',
    `create_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`         bigint                DEFAULT NULL COMMENT '更新人ID',
    `update_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    `active_config_name` varchar(128)
        GENERATED ALWAYS AS (IF(`is_deleted` = 0, `config_name`, NULL)) STORED
        COMMENT '有效配置名称；逻辑删除下唯一约束辅助列，is_deleted=0 时为 config_name，否则为 NULL',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_infra_file_storage_config_tenant_active_name` (`tenant_id`, `active_config_name`) COMMENT '租户内有效配置名称唯一',
    UNIQUE KEY `uk_infra_file_storage_config_tenant_default` (`tenant_id`, `default_marker`) COMMENT '租户内默认配置唯一',
    INDEX `idx_infra_file_storage_config_tenant_type` (`tenant_id`, `storage_type`) COMMENT '存储类型查询',
    INDEX `idx_infra_file_storage_config_tenant_status` (`tenant_id`, `status`) COMMENT '状态查询',
    INDEX `idx_infra_file_storage_config_tenant_deleted` (`tenant_id`, `is_deleted`) COMMENT '逻辑删除查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='基础设施-文件存储配置表';

DROP TABLE IF EXISTS `infra_file_metadata`;
CREATE TABLE `infra_file_metadata`
(
    `id`                 bigint        NOT NULL AUTO_INCREMENT COMMENT '主键ID（文件ID）',
    `file_config_id`     bigint                 DEFAULT NULL COMMENT '文件配置ID：关联 infra_file_storage_config.id，NULL表示默认配置',
    `original_name`      varchar(255)  NOT NULL COMMENT '原始文件名',
    `storage_path`       varchar(512)  NOT NULL COMMENT '存储路径',
    `file_url`           varchar(1024) NOT NULL COMMENT '文件访问URL',
    `content_type`       varchar(128)           DEFAULT NULL COMMENT '文件MIME类型',
    `size_bytes`         bigint        NOT NULL COMMENT '文件大小（字节）',
    `content_sha256`     varchar(64)            DEFAULT NULL COMMENT '文件内容SHA-256哈希',

    `tenant_id`          bigint        NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `is_deleted`         tinyint       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
    `version`            int           NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`          bigint                 DEFAULT NULL COMMENT '上传人ID',
    `create_time`        datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    `update_by`          bigint                 DEFAULT NULL COMMENT '更新人ID',
    `update_time`        datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    `file_config_marker` bigint
                         GENERATED ALWAYS AS (COALESCE(`file_config_id`, 0)) STORED
        COMMENT '文件配置标记；file_config_id 为 NULL 时转为 0，用于唯一约束',
    `active_storage_path` varchar(512)
        GENERATED ALWAYS AS (IF(`is_deleted` = 0, `storage_path`, NULL)) STORED
        COMMENT '有效存储路径；逻辑删除下唯一约束辅助列，is_deleted=0 时为 storage_path，否则为 NULL',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_infra_file_metadata_tenant_config_path` (`tenant_id`, `file_config_marker`, `active_storage_path`) COMMENT '租户内有效文件路径唯一',
    INDEX `idx_infra_file_metadata_tenant_sha256` (`tenant_id`, `content_sha256`) COMMENT '秒传查询',
    INDEX `idx_infra_file_metadata_tenant_time` (`tenant_id`, `create_time`) COMMENT '时间查询',
    INDEX `idx_infra_file_metadata_tenant_deleted` (`tenant_id`, `is_deleted`) COMMENT '逻辑删除查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='基础设施-文件元数据表';

DROP TABLE IF EXISTS `infra_file_content`;
CREATE TABLE `infra_file_content`
(
    `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `file_config_id`  bigint       NOT NULL COMMENT '文件配置ID：关联 infra_file_storage_config.id',
    `storage_path`    varchar(512) NOT NULL COMMENT '文件路径',
    `content`         mediumblob   NOT NULL COMMENT '文件内容（二进制）',
    `tenant_id`       bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_infra_file_content_config_path` (`file_config_id`, `storage_path`) COMMENT '文件配置路径唯一',
    INDEX `idx_infra_file_content_tenant_time` (`tenant_id`, `create_time`) COMMENT '租户时间查询',
    INDEX `idx_infra_file_content_time` (`create_time`) COMMENT '时间查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='基础设施-文件内容表';

DROP TABLE IF EXISTS `infra_data_source`;
CREATE TABLE `infra_data_source`
(
    `id`                          bigint        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `data_source_name`            varchar(128)  NOT NULL COMMENT '数据源名称：租户内唯一',
    `database_type`               varchar(32)   NOT NULL COMMENT '数据库类型：MySQL、PostgreSQL、Oracle、SQLServer',
    `jdbc_url`                    varchar(1024) NOT NULL COMMENT 'JDBC 连接串',
    `database_username`           varchar(255)  NOT NULL COMMENT '数据库用户名',
    `encrypted_database_password` varbinary(512) NOT NULL COMMENT '加密后的数据库密码',
    `status`                      tinyint       NOT NULL DEFAULT 0 COMMENT '状态：0-启用；1-禁用',
    `description`                 varchar(500)           DEFAULT NULL COMMENT '描述',

    `tenant_id`                   bigint        NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `is_deleted`                  tinyint       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
    `version`                     int           NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`                   bigint                 DEFAULT NULL COMMENT '创建人ID',
    `create_time`                 datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`                   bigint                 DEFAULT NULL COMMENT '更新人ID',
    `update_time`                 datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    `active_data_source_name`     varchar(128)
                                  GENERATED ALWAYS AS (IF(`is_deleted` = 0, `data_source_name`, NULL)) STORED
        COMMENT '有效数据源名称；逻辑删除下唯一约束辅助列，is_deleted=0 时为 data_source_name，否则为 NULL',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_infra_data_source_tenant_active_name` (`tenant_id`, `active_data_source_name`) COMMENT '租户内有效数据源名称唯一',
    INDEX `idx_infra_data_source_tenant_type` (`tenant_id`, `database_type`) COMMENT '数据库类型查询',
    INDEX `idx_infra_data_source_tenant_status` (`tenant_id`, `status`) COMMENT '状态查询',
    INDEX `idx_infra_data_source_tenant_deleted` (`tenant_id`, `is_deleted`) COMMENT '逻辑删除查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='基础设施-数据源配置表';

DROP TABLE IF EXISTS `infra_api_access_log`;
CREATE TABLE `infra_api_access_log`
(
    `id`                 bigint        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `trace_id`           varchar(64)   NOT NULL COMMENT '链路追踪ID',
    `user_id`            bigint        NOT NULL DEFAULT 0 COMMENT '用户ID：0-未登录',
    `user_type`          tinyint       NOT NULL DEFAULT 0 COMMENT '用户类型：1-会员；2-管理员',
    `application_name`   varchar(128)  NOT NULL COMMENT '应用名（微服务名）',
    `ip_address`         varchar(45)   NOT NULL COMMENT '请求来源 IP',
    `user_agent`         varchar(512)  NOT NULL COMMENT '客户端 User-Agent',
    `http_method`        varchar(16)   NOT NULL COMMENT 'HTTP 方法',
    `request_url`        varchar(1024) NOT NULL COMMENT '请求 URL',
    `request_params` text                   DEFAULT NULL COMMENT '请求参数',
    `response_body`      text                   DEFAULT NULL COMMENT '响应结果',
    `operation_module`   varchar(64)            DEFAULT NULL COMMENT '操作模块',
    `operation_name`     varchar(64)            DEFAULT NULL COMMENT '操作名称',
    `operation_type`     tinyint                DEFAULT NULL COMMENT '操作类型：0-其它；1-查询；2-新增；3-修改；4-删除；5-导出；6-导入',
    `start_time`         datetime      NOT NULL COMMENT '请求开始时间',
    `end_time`           datetime      NOT NULL COMMENT '请求结束时间',
    `duration_ms`        int           NOT NULL COMMENT '执行时长（毫秒）',
    `http_status_code`   int           NOT NULL DEFAULT 0 COMMENT 'HTTP状态码',
    `result_code`        int           NOT NULL DEFAULT 0 COMMENT '业务结果码：0-成功',
    `result_message`     varchar(512)           DEFAULT NULL COMMENT '业务结果提示',
    `tenant_id`          bigint        NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `create_time`        datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (`id`),
    INDEX `idx_infra_api_access_log_trace` (`trace_id`) COMMENT '链路查询',
    INDEX `idx_infra_api_access_log_user_time` (`user_id`, `create_time`) COMMENT '用户查询',
    INDEX `idx_infra_api_access_log_time` (`create_time`) COMMENT '时间查询',
    INDEX `idx_infra_api_access_log_tenant_time` (`tenant_id`, `create_time`) COMMENT '租户查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='基础设施-API 访问日志表';

DROP TABLE IF EXISTS `infra_api_error_log`;
CREATE TABLE `infra_api_error_log`
(
    `id`                           bigint        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `trace_id`                     varchar(64)   NOT NULL COMMENT '链路追踪ID',
    `user_id`                      bigint        NOT NULL DEFAULT 0 COMMENT '用户ID：0-未登录',
    `user_type`                    tinyint       NOT NULL DEFAULT 0 COMMENT '用户类型：1-会员；2-管理员',
    `application_name`             varchar(128)  NOT NULL COMMENT '应用名（微服务名）',
    `ip_address`                   varchar(45)   NOT NULL COMMENT '请求来源 IP',
    `user_agent`                   varchar(512)  NOT NULL COMMENT '客户端 User-Agent',
    `http_method`                  varchar(16)   NOT NULL COMMENT 'HTTP 方法',
    `request_url`                  varchar(1024) NOT NULL COMMENT '请求 URL',
    `request_params`           text                   DEFAULT NULL COMMENT '请求参数',
    `exception_name`               varchar(128)  NOT NULL COMMENT '异常类简称',
    `exception_message`            text          NOT NULL COMMENT '异常消息',
    `exception_root_cause_message` text          NOT NULL COMMENT '异常根因消息',
    `exception_stack_trace`        text          NOT NULL COMMENT '异常堆栈轨迹',
    `exception_class_name`         varchar(512)  NOT NULL COMMENT '异常发生的类全名',
    `exception_file_name`          varchar(512)  NOT NULL COMMENT '异常发生的源文件名',
    `exception_method_name`        varchar(512)  NOT NULL COMMENT '异常发生的方法名',
    `exception_line_number`        int           NOT NULL COMMENT '异常发生的源码行号',
    `exception_time`               datetime      NOT NULL COMMENT '异常发生时间',
    `process_status`               tinyint       NOT NULL DEFAULT 0 COMMENT '处理状态：0-未处理；1-已处理；2-已忽略',
    `process_time`                 datetime              DEFAULT NULL COMMENT '处理时间',
    `process_by`                   bigint                 DEFAULT NULL COMMENT '处理人ID',
    `tenant_id`                    bigint        NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `create_time`                  datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`                  datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (`id`),
    INDEX `idx_infra_api_error_log_trace` (`trace_id`) COMMENT '链路查询',
    INDEX `idx_infra_api_error_log_status_time` (`process_status`, `create_time`) COMMENT '处理状态查询',
    INDEX `idx_infra_api_error_log_user_time` (`user_id`, `create_time`) COMMENT '用户查询',
    INDEX `idx_infra_api_error_log_exception_time` (`exception_name`, `create_time`) COMMENT '异常类型查询',
    INDEX `idx_infra_api_error_log_time` (`create_time`) COMMENT '时间查询',
    INDEX `idx_infra_api_error_log_tenant_time` (`tenant_id`, `create_time`) COMMENT '租户查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='基础设施-API错误日志表';

DROP TABLE IF EXISTS `infra_codegen_table`;
CREATE TABLE `infra_codegen_table`
(
    `id`                    bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID（代码生成表ID）',
    `data_source_id`        bigint       NOT NULL COMMENT '数据源配置ID：关联 infra_data_source.id',
    `scene`                 tinyint      NOT NULL DEFAULT 1 COMMENT '生成场景：1-管理后台；2-用户 APP',
    `table_name`            varchar(200) NOT NULL COMMENT '表名称',
    `table_comment`         varchar(500) NOT NULL COMMENT '表描述',
    `description`           varchar(500)          DEFAULT NULL COMMENT '描述',
    `module_name`           varchar(64)  NOT NULL COMMENT '模块名',
    `business_name`         varchar(64)  NOT NULL COMMENT '业务名',
    `class_name`            varchar(100) NOT NULL COMMENT '类名称',
    `class_comment`         varchar(128) NOT NULL COMMENT '类描述',
    `author`                varchar(64)  NOT NULL COMMENT '作者',
    `template_type`         tinyint      NOT NULL DEFAULT 1 COMMENT '模板类型：1-单表；2-树表；3-主子表',
    `frontend_type`            tinyint      NOT NULL COMMENT '前端类型：业务自定义枚举',
    `parent_menu_id`        bigint                DEFAULT NULL COMMENT '父菜单ID：关联 iam_menu.id',
    `master_table_id`       bigint                DEFAULT NULL COMMENT '主表ID：关联 infra_codegen_table.id',
    `sub_join_column_id`    bigint                DEFAULT NULL COMMENT '子表关联主表的字段ID',
    `is_sub_join_many`      tinyint      NOT NULL DEFAULT 0 COMMENT '主表与子表是否一对多：0-否；1-是',
    `tree_parent_column_id` bigint                DEFAULT NULL COMMENT '树表的父字段ID',
    `tree_name_column_id`   bigint                DEFAULT NULL COMMENT '树表的名字字段ID',

    `tenant_id`             bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `is_deleted`            tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
    `version`               int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`             bigint                DEFAULT NULL COMMENT '创建人ID',
    `create_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`             bigint                DEFAULT NULL COMMENT '更新人ID',
    `update_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    `active_table_name`     varchar(200) GENERATED ALWAYS AS (
                                IF(`is_deleted` = 0, `table_name`, NULL)
                                ) STORED COMMENT '有效表名；逻辑删除下唯一约束辅助列，is_deleted=0 时为 table_name，否则为 NULL',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_infra_codegen_table_tenant_source_name` (`tenant_id`, `data_source_id`, `active_table_name`) COMMENT '租户内数据源表名唯一',
    INDEX `idx_infra_codegen_table_tenant_menu` (`tenant_id`, `parent_menu_id`) COMMENT '父菜单查询',
    INDEX `idx_infra_codegen_table_tenant_master` (`tenant_id`, `master_table_id`) COMMENT '主表查询',
    INDEX `idx_infra_codegen_table_tenant_deleted` (`tenant_id`, `is_deleted`) COMMENT '逻辑删除查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='基础设施-代码生成表定义';

DROP TABLE IF EXISTS `infra_codegen_column`;
CREATE TABLE `infra_codegen_column`
(
    `id`                       bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `codegen_table_id`         bigint       NOT NULL COMMENT '代码生成表ID：关联 infra_codegen_table.id',
    `column_name`              varchar(200) NOT NULL COMMENT '数据库字段名',
    `data_type`                varchar(100) NOT NULL COMMENT '数据库字段类型',
    `column_comment`           varchar(500) NOT NULL COMMENT '数据库字段描述',
    `is_nullable`              tinyint      NOT NULL DEFAULT 0 COMMENT '是否允许为空：0-否；1-是',
    `is_primary_key`           tinyint      NOT NULL DEFAULT 0 COMMENT '是否主键：0-否；1-是',
    `ordinal_position`         int          NOT NULL COMMENT '字段在表中的序号：从1开始',
    `java_type`                varchar(32)  NOT NULL COMMENT 'Java属性类型',
    `java_field`               varchar(64)  NOT NULL COMMENT 'Java属性名（驼峰）',
    `dict_type_code`           varchar(200)          DEFAULT NULL COMMENT '字典类型编码',
    `example_value`            varchar(64)           DEFAULT NULL COMMENT '数据示例',
    `is_create_operation`      tinyint      NOT NULL DEFAULT 1 COMMENT '是否为Create操作字段：0-否；1-是',
    `is_update_operation`      tinyint      NOT NULL DEFAULT 1 COMMENT '是否为Update操作字段：0-否；1-是',
    `is_list_operation`        tinyint      NOT NULL DEFAULT 1 COMMENT '是否为List查询字段：0-否；1-是',
    `list_operation_condition` varchar(32)  NOT NULL DEFAULT '=' COMMENT 'List查询条件类型：=、!=、>、>=、<、<=、LIKE、BETWEEN、IN',
    `is_list_operation_result` tinyint      NOT NULL DEFAULT 1 COMMENT '是否为List返回字段：0-否；1-是',
    `html_type`                varchar(32)  NOT NULL COMMENT '前端显示类型：业务自定义枚举',

    `tenant_id`                bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `is_deleted`               tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
    `version`                  int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`                bigint                DEFAULT NULL COMMENT '创建人ID',
    `create_time`              datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`                bigint                DEFAULT NULL COMMENT '更新人ID',
    `update_time`              datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    `active_column_name`       varchar(200)
                               GENERATED ALWAYS AS (IF(`is_deleted` = 0, `column_name`, NULL)) STORED
        COMMENT '有效字段名；逻辑删除下唯一约束辅助列，is_deleted=0 时为 column_name，否则为 NULL',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_infra_codegen_column_tenant_table_name` (`tenant_id`, `codegen_table_id`, `active_column_name`) COMMENT '租户内表字段唯一',
    INDEX `idx_infra_codegen_column_tenant_table_pos` (`tenant_id`, `codegen_table_id`, `ordinal_position`) COMMENT '表字段排序查询',
    INDEX `idx_infra_codegen_column_tenant_deleted` (`tenant_id`, `is_deleted`) COMMENT '逻辑删除查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='基础设施-代码生成字段定义表';

-- =====================================================================
-- SaaS 租户模块
-- =====================================================================

DROP TABLE IF EXISTS `saas_tenant`;
CREATE TABLE `saas_tenant`
(
    `id`                    bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID（租户ID）',
    `tenant_code`           varchar(64)  NOT NULL COMMENT '租户编码：全局唯一',
    `tenant_name`           varchar(128) NOT NULL COMMENT '租户名称',
    `contact_user_id`       bigint                DEFAULT NULL COMMENT '联系人用户ID：关联 iam_user.id',
    `contact_name`          varchar(64)  NOT NULL COMMENT '联系人姓名',
    `contact_phone_number`  varchar(20)           DEFAULT NULL COMMENT '联系人手机号',
    `contact_email`         varchar(254)          DEFAULT NULL COMMENT '联系人邮箱',
    `status`                tinyint      NOT NULL DEFAULT 0 COMMENT '租户状态：0-正常；1-停用；2-过期',
    `package_id`            bigint       NOT NULL COMMENT '租户套餐ID：关联 saas_package.id',
    `expire_time`           datetime     NOT NULL COMMENT '过期时间',
    `max_account_count`     int          NOT NULL DEFAULT 0 COMMENT '最大账号数量：0-不限制',

    `is_deleted`            tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
    `version`               int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`             bigint                DEFAULT NULL COMMENT '创建人ID',
    `create_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`             bigint                DEFAULT NULL COMMENT '更新人ID',
    `update_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    `active_tenant_code`    varchar(64)
                            GENERATED ALWAYS AS (IF(`is_deleted` = 0, `tenant_code`, NULL)) STORED
        COMMENT '有效租户编码；逻辑删除下唯一约束辅助列，is_deleted=0 时为 tenant_code，否则为 NULL',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_saas_tenant_active_tenant_code` (`active_tenant_code`) COMMENT '有效租户编码全局唯一',
    INDEX `idx_saas_tenant_package` (`package_id`) COMMENT '套餐查询',
    INDEX `idx_saas_tenant_status` (`status`) COMMENT '状态查询',
    INDEX `idx_saas_tenant_expire_time` (`expire_time`) COMMENT '过期时间查询',
    INDEX `idx_saas_tenant_deleted` (`is_deleted`) COMMENT '逻辑删除查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='SaaS-租户表';

DROP TABLE IF EXISTS `saas_tenant_domain`;
CREATE TABLE `saas_tenant_domain`
(
    `id`               bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`        bigint       NOT NULL COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `domain`           varchar(255) NOT NULL COMMENT '域名',
    `is_primary`       tinyint      NOT NULL DEFAULT 0 COMMENT '是否主域名：0-否；1-是',

    `is_deleted`       tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
    `version`          int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`        bigint                DEFAULT NULL COMMENT '创建人ID',
    `create_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        bigint                DEFAULT NULL COMMENT '更新人ID',
    `update_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    `active_domain`    varchar(255)
                       GENERATED ALWAYS AS (IF(`is_deleted` = 0, `domain`, NULL)) STORED
        COMMENT '有效域名；逻辑删除下唯一约束辅助列，is_deleted=0 时为 domain，否则为 NULL',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_saas_tenant_domain_active_domain` (`active_domain`) COMMENT '有效域名全局唯一',
    INDEX `idx_saas_tenant_domain_tenant` (`tenant_id`) COMMENT '租户查询',
    INDEX `idx_saas_tenant_domain_tenant_deleted` (`tenant_id`, `is_deleted`) COMMENT '逻辑删除查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='SaaS-租户域名表';

DROP TABLE IF EXISTS `saas_package`;
CREATE TABLE `saas_package`
(
    `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID（套餐ID）',
    `package_name`      varchar(128) NOT NULL COMMENT '套餐名称：全局唯一',
    `status`            tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0-正常；1-停用',
    `description`       varchar(500)          DEFAULT NULL COMMENT '描述',

    `is_deleted`        tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
    `version`           int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`         bigint                DEFAULT NULL COMMENT '创建人ID',
    `create_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`         bigint                DEFAULT NULL COMMENT '更新人ID',
    `update_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    `active_package_name` varchar(128)
        GENERATED ALWAYS AS (IF(`is_deleted` = 0, `package_name`, NULL)) STORED
        COMMENT '有效套餐名称；逻辑删除下唯一约束辅助列，is_deleted=0 时为 package_name，否则为 NULL',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_saas_package_active_package_name` (`active_package_name`) COMMENT '有效套餐名称全局唯一',
    INDEX `idx_saas_package_status` (`status`) COMMENT '状态查询',
    INDEX `idx_saas_package_deleted` (`is_deleted`) COMMENT '逻辑删除查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='SaaS-租户套餐表';

DROP TABLE IF EXISTS `saas_package_menu`;
CREATE TABLE `saas_package_menu`
(
    `id`          bigint   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `package_id`  bigint   NOT NULL COMMENT '套餐ID：关联 saas_package.id',
    `menu_id`     bigint   NOT NULL COMMENT '菜单ID：关联 iam_menu.id',
    `create_by`   bigint            DEFAULT NULL COMMENT '创建人ID',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_saas_package_menu_package_menu` (`package_id`, `menu_id`) COMMENT '套餐菜单唯一',
    INDEX `idx_saas_package_menu_menu` (`menu_id`) COMMENT '菜单查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='SaaS-租户套餐与菜单关联表';

-- =====================================================================
-- Msg 消息模块
-- =====================================================================

DROP TABLE IF EXISTS `msg_sms_template`;
CREATE TABLE `msg_sms_template`
(
    `id`                    bigint        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `template_code`         varchar(64)   NOT NULL COMMENT '模板编码：全局唯一',
    `template_name`         varchar(128)  NOT NULL COMMENT '模板名称',
    `template_type`         tinyint       NOT NULL COMMENT '模板类型：1-验证码；2-通知；3-营销',
    `channel_id`            bigint        NOT NULL COMMENT '短信渠道ID：关联 msg_sms_channel.id',
    `channel_code`          varchar(64)   NOT NULL COMMENT '短信渠道编码（冗余）',
    `provider_template_id`  varchar(64)   NOT NULL COMMENT '第三方平台的模板编号',
    `content`               varchar(1024) NOT NULL COMMENT '模板内容：使用 {key} 占位',
    `params`                json                   DEFAULT NULL COMMENT '模板参数数组',
    `status`                tinyint       NOT NULL DEFAULT 0 COMMENT '状态：0-启用；1-禁用',
    `description`           varchar(255)           DEFAULT NULL COMMENT '描述',

    `is_deleted`            tinyint       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
    `version`               int           NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`             bigint                 DEFAULT NULL COMMENT '创建人ID',
    `create_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`             bigint                 DEFAULT NULL COMMENT '更新人ID',
    `update_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    `active_template_code`  varchar(64)
                            GENERATED ALWAYS AS (IF(`is_deleted` = 0, `template_code`, NULL)) STORED
        COMMENT '有效模板编码；逻辑删除下唯一约束辅助列，is_deleted=0 时为 template_code，否则为 NULL',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_msg_sms_template_active_code` (`active_template_code`) COMMENT '有效模板编码全局唯一',
    INDEX `idx_msg_sms_template_channel` (`channel_id`) COMMENT '渠道查询',
    INDEX `idx_msg_sms_template_type` (`template_type`) COMMENT '类型查询',
    INDEX `idx_msg_sms_template_status` (`status`) COMMENT '状态查询',
    INDEX `idx_msg_sms_template_deleted` (`is_deleted`) COMMENT '逻辑删除查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='消息-短信模板表';

DROP TABLE IF EXISTS `msg_sms_channel`;
CREATE TABLE `msg_sms_channel`
(
    `id`                      bigint        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `channel_code`            varchar(64)   NOT NULL COMMENT '渠道编码：全局唯一',
    `channel_name`            varchar(128)  NOT NULL COMMENT '渠道名称',
    `signature`               varchar(64)   NOT NULL COMMENT '短信签名',
    `encrypted_access_key`    varbinary(512) NOT NULL COMMENT '加密后的 AccessKey',
    `encrypted_access_secret` varbinary(512) NOT NULL COMMENT '加密后的 AccessSecret',
    `callback_url`            varchar(512)           DEFAULT NULL COMMENT '短信状态回调 URL',
    `status`                  tinyint       NOT NULL DEFAULT 0 COMMENT '状态：0-启用；1-禁用',
    `description`             varchar(255)           DEFAULT NULL COMMENT '描述',

    `is_deleted`              tinyint       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
    `version`                 int           NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`               bigint                 DEFAULT NULL COMMENT '创建人ID',
    `create_time`             datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`               bigint                 DEFAULT NULL COMMENT '更新人ID',
    `update_time`             datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    `active_channel_code`     varchar(64)
                              GENERATED ALWAYS AS (IF(`is_deleted` = 0, `channel_code`, NULL)) STORED
        COMMENT '有效渠道编码；逻辑删除下唯一约束辅助列，is_deleted=0 时为 channel_code，否则为 NULL',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_msg_sms_channel_active_code` (`active_channel_code`) COMMENT '有效渠道编码全局唯一',
    INDEX `idx_msg_sms_channel_status` (`status`) COMMENT '状态查询',
    INDEX `idx_msg_sms_channel_deleted` (`is_deleted`) COMMENT '逻辑删除查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='消息-短信渠道配置表';

DROP TABLE IF EXISTS `msg_sms_send_log`;
CREATE TABLE `msg_sms_send_log`
(
    `id`                       bigint        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `channel_id`               bigint        NOT NULL COMMENT '短信渠道ID',
    `channel_code`             varchar(64)   NOT NULL COMMENT '短信渠道编码（冗余）',
    `template_id`              bigint        NOT NULL COMMENT '模板ID',
    `template_code`            varchar(64)   NOT NULL COMMENT '模板编码（快照）',
    `template_type`            tinyint       NOT NULL COMMENT '模板类型：1-验证码；2-通知；3-营销',
    `template_content`         varchar(1024) NOT NULL COMMENT '渲染后的短信内容（快照）',
    `template_params`          json                   DEFAULT NULL COMMENT '模板参数（快照）',
    `provider_template_id`     varchar(64)   NOT NULL COMMENT '第三方平台的模板编号',
    `phone_number`             varchar(20)   NOT NULL COMMENT '手机号',
    `user_id`                  bigint                 DEFAULT NULL COMMENT '用户ID',
    `user_type`                tinyint                DEFAULT NULL COMMENT '用户类型：1-会员；2-管理员',
    `send_status`              tinyint       NOT NULL DEFAULT 0 COMMENT '发送状态：0-初始化；10-发送成功；20-发送失败；30-不发送',
    `send_time`                datetime              DEFAULT NULL COMMENT '发送时间',
    `provider_send_code`       varchar(64)            DEFAULT NULL COMMENT '服务商发送结果编码',
    `provider_send_message`    varchar(512)           DEFAULT NULL COMMENT '服务商发送结果消息',
    `provider_request_id`      varchar(128)           DEFAULT NULL COMMENT '服务商请求ID',
    `provider_serial_number`   varchar(128)           DEFAULT NULL COMMENT '服务商流水号',
    `receive_status`           tinyint       NOT NULL DEFAULT 0 COMMENT '接收状态：0-等待结果；10-接收成功；20-接收失败',
    `receive_time`             datetime              DEFAULT NULL COMMENT '接收时间',
    `provider_receive_code`    varchar(64)            DEFAULT NULL COMMENT '服务商接收结果编码',
    `provider_receive_message` varchar(512)           DEFAULT NULL COMMENT '服务商接收结果说明',
    `tenant_id`                bigint        NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `create_time`              datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (`id`),
    INDEX `idx_msg_sms_send_log_phone_time` (`phone_number`, `create_time`) COMMENT '手机号查询',
    INDEX `idx_msg_sms_send_log_user_time` (`user_id`, `create_time`) COMMENT '用户查询',
    INDEX `idx_msg_sms_send_log_template_time` (`template_code`, `create_time`) COMMENT '模板查询',
    INDEX `idx_msg_sms_send_log_status_time` (`send_status`, `create_time`) COMMENT '状态查询',
    INDEX `idx_msg_sms_send_log_tenant_time` (`tenant_id`, `create_time`) COMMENT '租户查询',
    INDEX `idx_msg_sms_send_log_time` (`create_time`) COMMENT '时间查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='消息-短信发送日志表';

DROP TABLE IF EXISTS `msg_sms_code`;
CREATE TABLE `msg_sms_code`
(
    `id`                 bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `phone_number`       varchar(20)  NOT NULL COMMENT '手机号（E.164 格式）',
    `code_hash`          varchar(128) NOT NULL COMMENT '验证码哈希',
    `scene`              tinyint      NOT NULL COMMENT '发送场景：1-登录；2-注册；3-重置密码；4-修改手机；5-绑定手机；6-其他',
    `tenant_id`          bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `request_ip_address`         varchar(45)  NOT NULL COMMENT '请求 IP 地址',
    `user_agent` varchar(512)          DEFAULT NULL COMMENT '客户端 User-Agent',
    `is_used`            tinyint      NOT NULL DEFAULT 0 COMMENT '是否已使用：0-未使用；1-已使用',
    `use_time`           datetime              DEFAULT NULL COMMENT '使用时间',
    `use_ip_address`             varchar(45)           DEFAULT NULL COMMENT '使用 IP 地址',
    `daily_sequence`     smallint     NOT NULL DEFAULT 1 COMMENT '当日发送的第几条',
    `expire_time`        datetime     NOT NULL COMMENT '过期时间',
    `create_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (`id`),
    INDEX `idx_msg_sms_code_phone_scene_time` (`phone_number`, `scene`, `create_time`) COMMENT '手机号场景查询',
    INDEX `idx_msg_sms_code_expire` (`expire_time`) COMMENT '过期查询',
    INDEX `idx_msg_sms_code_tenant_time` (`tenant_id`, `create_time`) COMMENT '租户查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='消息-短信验证码表';

DROP TABLE IF EXISTS `msg_inbox_template`;
CREATE TABLE `msg_inbox_template`
(
    `id`                    bigint        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `template_code`         varchar(64)   NOT NULL COMMENT '模板编码：全局唯一',
    `template_name`         varchar(128)  NOT NULL COMMENT '模板名称',
    `template_type`         tinyint       NOT NULL COMMENT '模板类型：1-通知公告；2-系统消息',
    `sender_name`           varchar(64)   NOT NULL COMMENT '发送人名称',
    `content`               varchar(1024) NOT NULL COMMENT '模板内容：使用 {key} 占位',
    `params`                json                   DEFAULT NULL COMMENT '模板参数数组',
    `status`                tinyint       NOT NULL DEFAULT 0 COMMENT '状态：0-启用；1-禁用',
    `description`           varchar(255)           DEFAULT NULL COMMENT '描述',

    `is_deleted`            tinyint       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
    `version`               int           NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`             bigint                 DEFAULT NULL COMMENT '创建人ID',
    `create_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`             bigint                 DEFAULT NULL COMMENT '更新人ID',
    `update_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    `active_template_code`  varchar(64)
                            GENERATED ALWAYS AS (IF(`is_deleted` = 0, `template_code`, NULL)) STORED
        COMMENT '有效模板编码；逻辑删除下唯一约束辅助列，is_deleted=0 时为 template_code，否则为 NULL',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_msg_inbox_template_active_code` (`active_template_code`) COMMENT '有效模板编码全局唯一',
    INDEX `idx_msg_inbox_template_type` (`template_type`) COMMENT '类型查询',
    INDEX `idx_msg_inbox_template_status` (`status`) COMMENT '状态查询',
    INDEX `idx_msg_inbox_template_deleted` (`is_deleted`) COMMENT '逻辑删除查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='消息-站内信模板表';

DROP TABLE IF EXISTS `msg_inbox_message`;
CREATE TABLE `msg_inbox_message`
(
    `id`                   bigint        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`              bigint        NOT NULL COMMENT '接收用户ID',
    `user_type`            tinyint       NOT NULL COMMENT '用户类型：1-会员；2-管理员',
    `template_id`          bigint        NOT NULL COMMENT '模板ID',
    `template_code`        varchar(64)   NOT NULL COMMENT '模板编码（快照）',
    `template_type`        tinyint       NOT NULL COMMENT '模板类型（快照）：1-通知公告；2-系统消息',
    `template_sender_name` varchar(64)   NOT NULL COMMENT '模板发送人名称（快照）',
    `template_content`     varchar(1024) NOT NULL COMMENT '渲染后的消息内容（快照）',
    `template_params`      json                   DEFAULT NULL COMMENT '模板参数（快照）',
    `is_read`              tinyint       NOT NULL DEFAULT 0 COMMENT '是否已读：0-未读；1-已读',
    `read_time`            datetime              DEFAULT NULL COMMENT '阅读时间',

    `tenant_id`            bigint        NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `is_deleted`           tinyint       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
    `version`              int           NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`            bigint                 DEFAULT NULL COMMENT '创建人ID',
    `create_time`          datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`            bigint                 DEFAULT NULL COMMENT '更新人ID',
    `update_time`          datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (`id`),
    INDEX `idx_msg_inbox_message_user_unread` (`user_id`, `user_type`, `is_read`, `create_time`) COMMENT '未读查询',
    INDEX `idx_msg_inbox_message_user_time` (`user_id`, `user_type`, `create_time`) COMMENT '用户查询',
    INDEX `idx_msg_inbox_message_tenant` (`tenant_id`) COMMENT '租户查询',
    INDEX `idx_msg_inbox_message_deleted` (`is_deleted`) COMMENT '逻辑删除查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='消息-站内信消息表';

DROP TABLE IF EXISTS `msg_announcement`;
CREATE TABLE `msg_announcement`
(
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID（公告ID）',
    `title`       varchar(255) NOT NULL COMMENT '公告标题',
    `content`     text         NOT NULL COMMENT '公告内容（HTML富文本）',
    `notice_type` tinyint      NOT NULL COMMENT '公告类型：1-通知；2-公告',
    `status`      tinyint      NOT NULL DEFAULT 0 COMMENT '公告状态：0-正常；1-关闭',

    `tenant_id`   bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `is_deleted`  tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
    `version`     int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`   bigint                DEFAULT NULL COMMENT '创建人ID',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   bigint                DEFAULT NULL COMMENT '更新人ID',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (`id`),
    INDEX `idx_msg_announcement_tenant_type` (`tenant_id`, `notice_type`) COMMENT '类型查询',
    INDEX `idx_msg_announcement_tenant_status_time` (`tenant_id`, `status`, `create_time`) COMMENT '状态查询',
    INDEX `idx_msg_announcement_tenant_deleted` (`tenant_id`, `is_deleted`) COMMENT '逻辑删除查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='消息-通知公告表';

DROP TABLE IF EXISTS `msg_mail_template`;
CREATE TABLE `msg_mail_template`
(
    `id`                    bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `template_code`         varchar(64)  NOT NULL COMMENT '模板编码：全局唯一',
    `template_name`         varchar(128) NOT NULL COMMENT '模板名称',
    `mail_account_id`       bigint       NOT NULL COMMENT '发送邮箱账号ID：关联 msg_mail_account.id',
    `sender_name`           varchar(64)           DEFAULT NULL COMMENT '发送人名称',
    `subject`               varchar(255) NOT NULL COMMENT '邮件主题：使用 {key} 占位',
    `content`               mediumtext   NOT NULL COMMENT '邮件内容（HTML富文本）：使用 {key} 占位',
    `params`                json                  DEFAULT NULL COMMENT '模板参数数组',
    `status`                tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0-启用；1-禁用',
    `description`           varchar(255)          DEFAULT NULL COMMENT '描述',

    `is_deleted`            tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
    `version`               int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`             bigint                DEFAULT NULL COMMENT '创建人ID',
    `create_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`             bigint                DEFAULT NULL COMMENT '更新人ID',
    `update_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    `active_template_code`  varchar(64)
                            GENERATED ALWAYS AS (IF(`is_deleted` = 0, `template_code`, NULL)) STORED
        COMMENT '有效模板编码；逻辑删除下唯一约束辅助列，is_deleted=0 时为 template_code，否则为 NULL',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_msg_mail_template_active_code` (`active_template_code`) COMMENT '有效模板编码全局唯一',
    INDEX `idx_msg_mail_template_account` (`mail_account_id`) COMMENT '邮箱账号查询',
    INDEX `idx_msg_mail_template_status` (`status`) COMMENT '状态查询',
    INDEX `idx_msg_mail_template_deleted` (`is_deleted`) COMMENT '逻辑删除查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='消息-邮件模板表';

DROP TABLE IF EXISTS `msg_mail_account`;
CREATE TABLE `msg_mail_account`
(
    `id`                      bigint        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `account_name`            varchar(64)   NOT NULL COMMENT '账号名称：全局唯一',
    `from_email`              varchar(254)  NOT NULL COMMENT '发件人邮箱地址',
    `sender_name`             varchar(64)            DEFAULT NULL COMMENT '发件人显示名称',
    `smtp_host`               varchar(255)  NOT NULL COMMENT 'SMTP 服务器域名',
    `smtp_port`               int           NOT NULL COMMENT 'SMTP 服务器端口',
    `smtp_username`           varchar(255)  NOT NULL COMMENT 'SMTP 登录用户名',
    `encrypted_smtp_password` varbinary(512) NOT NULL COMMENT '加密后的 SMTP 登录密码',
    `is_ssl_enabled`          tinyint       NOT NULL DEFAULT 0 COMMENT '是否启用 SSL：0-否；1-是',
    `is_starttls_enabled`     tinyint       NOT NULL DEFAULT 0 COMMENT '是否启用 STARTTLS：0-否；1-是',
    `status`                  tinyint       NOT NULL DEFAULT 0 COMMENT '状态：0-启用；1-禁用',
    `description`             varchar(255)           DEFAULT NULL COMMENT '描述',

    `is_deleted`              tinyint       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
    `version`                 int           NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`               bigint                 DEFAULT NULL COMMENT '创建人ID',
    `create_time`             datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`               bigint                 DEFAULT NULL COMMENT '更新人ID',
    `update_time`             datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    `active_account_name`     varchar(64)
                              GENERATED ALWAYS AS (IF(`is_deleted` = 0, `account_name`, NULL)) STORED
        COMMENT '有效账号名称；逻辑删除下唯一约束辅助列，is_deleted=0 时为 account_name，否则为 NULL',
    `active_from_email`       varchar(254)
                              GENERATED ALWAYS AS (IF(`is_deleted` = 0, `from_email`, NULL)) STORED
        COMMENT '有效发件邮箱；逻辑删除下唯一约束辅助列，is_deleted=0 时为 from_email，否则为 NULL',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_msg_mail_account_active_name` (`active_account_name`) COMMENT '有效账号名称全局唯一',
    UNIQUE KEY `uk_msg_mail_account_active_from_email` (`active_from_email`) COMMENT '有效发件邮箱全局唯一',
    INDEX `idx_msg_mail_account_status` (`status`) COMMENT '状态查询',
    INDEX `idx_msg_mail_account_deleted` (`is_deleted`) COMMENT '逻辑删除查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='消息-邮箱账号配置表';

DROP TABLE IF EXISTS `msg_mail_send_log`;
CREATE TABLE `msg_mail_send_log`
(
    `id`                   bigint        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`              bigint                 DEFAULT NULL COMMENT '接收用户ID',
    `user_type`            tinyint                DEFAULT NULL COMMENT '用户类型：1-会员；2-管理员',
    `to_recipients`        varchar(1024) NOT NULL COMMENT '收件人邮箱地址（多个逗号分隔）',
    `cc_recipients`        varchar(1024)          DEFAULT NULL COMMENT '抄送邮箱地址',
    `bcc_recipients`       varchar(1024)          DEFAULT NULL COMMENT '密送邮箱地址',
    `mail_account_id`      bigint        NOT NULL COMMENT '邮箱账号ID',
    `from_email`           varchar(254)  NOT NULL COMMENT '发件人邮箱地址',
    `template_id`          bigint        NOT NULL COMMENT '模板ID',
    `template_code`        varchar(64)   NOT NULL COMMENT '模板编码（快照）',
    `template_sender_name` varchar(64)            DEFAULT NULL COMMENT '模板发送人名称（快照）',
    `template_subject`     varchar(255)  NOT NULL COMMENT '邮件主题（快照）',
    `template_content`     mediumtext    NOT NULL COMMENT '邮件内容（快照）',
    `template_params`      json                   DEFAULT NULL COMMENT '模板参数（快照）',
    `send_status`          tinyint       NOT NULL DEFAULT 0 COMMENT '发送状态：0-初始化；10-发送成功；20-发送失败；30-不发送',
    `send_time`            datetime              DEFAULT NULL COMMENT '发送时间',
    `provider_message_id`  varchar(255)           DEFAULT NULL COMMENT '邮件服务商返回的消息 ID',
    `error_message`        varchar(4096)          DEFAULT NULL COMMENT '发送失败时的异常信息',
    `tenant_id`            bigint        NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `create_time`          datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (`id`),
    INDEX `idx_msg_mail_send_log_user_time` (`user_id`, `create_time`) COMMENT '用户查询',
    INDEX `idx_msg_mail_send_log_to_time` (`to_recipients`(255), `create_time`) COMMENT '收件人查询',
    INDEX `idx_msg_mail_send_log_status_time` (`send_status`, `create_time`) COMMENT '状态查询',
    INDEX `idx_msg_mail_send_log_tenant_time` (`tenant_id`, `create_time`) COMMENT '租户查询',
    INDEX `idx_msg_mail_send_log_time` (`create_time`) COMMENT '时间查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='消息-邮件发送日志表';

-- =====================================================================
-- Audit 审计模块
-- =====================================================================

DROP TABLE IF EXISTS `audit_operation_log`;
CREATE TABLE `audit_operation_log`
(
    `id`               bigint        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `trace_id`         varchar(64)   NOT NULL COMMENT '链路追踪ID',
    `user_id`          bigint        NOT NULL DEFAULT 0 COMMENT '操作用户ID：0-未登录',
    `user_type`        tinyint       NOT NULL DEFAULT 0 COMMENT '用户类型：1-会员；2-管理员',
    `ip_address`       varchar(45)            DEFAULT NULL COMMENT '操作来源 IP',
    `user_agent`       varchar(512)           DEFAULT NULL COMMENT '客户端 User-Agent',
    `http_method`      varchar(16)            DEFAULT NULL COMMENT 'HTTP 方法：GET、POST、PUT、DELETE',
    `request_url`      varchar(1024)          DEFAULT NULL COMMENT '请求 URL',
    `operation_module` varchar(64)   NOT NULL COMMENT '操作模块',
    `operation_name`   varchar(64)   NOT NULL COMMENT '操作名称',
    `business_id`      bigint        NOT NULL DEFAULT 0 COMMENT '业务数据ID',
    `action`           varchar(2000) NOT NULL COMMENT '操作内容描述',
    `is_success`       tinyint       NOT NULL DEFAULT 1 COMMENT '操作结果：0-失败；1-成功',
    `extra_data`       text                   DEFAULT NULL COMMENT '扩展数据（JSON格式，记录变更前后值）',
    `tenant_id`        bigint        NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `create_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (`id`),
    INDEX `idx_audit_operation_log_user_time` (`user_id`, `create_time`) COMMENT '用户查询',
    INDEX `idx_audit_operation_log_module_time` (`operation_module`, `create_time`) COMMENT '模块查询',
    INDEX `idx_audit_operation_log_time` (`create_time`) COMMENT '时间查询',
    INDEX `idx_audit_operation_log_tenant_time` (`tenant_id`, `create_time`) COMMENT '租户查询',
    INDEX `idx_audit_operation_log_trace` (`trace_id`) COMMENT '链路查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='审计-操作日志表';

DROP TABLE IF EXISTS `audit_login_log`;
CREATE TABLE `audit_login_log`
(
    `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `trace_id`     varchar(64)  NOT NULL COMMENT '链路追踪ID',
    `user_id`      bigint       NOT NULL DEFAULT 0 COMMENT '用户ID：0-未登录',
    `user_type`    tinyint      NOT NULL DEFAULT 0 COMMENT '用户类型：1-会员；2-管理员',
    `username`     varchar(30)  NOT NULL COMMENT '登录账号',
    `login_type`   tinyint      NOT NULL COMMENT '登录类型：100-账号密码；101-社交；102-短信；103-邮箱；104-一键登录',
    `login_result` tinyint      NOT NULL COMMENT '登录结果：0-成功；10-账号或密码不正确；20-用户被禁用；30-验证码不存在；31-验证码不正确；100-未知异常',
    `ip_address`   varchar(45)  NOT NULL COMMENT '登录来源 IP',
    `user_agent`   varchar(512) NOT NULL COMMENT '客户端 User-Agent',
    `tenant_id`    bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (`id`),
    INDEX `idx_audit_login_log_username_time` (`username`, `create_time`) COMMENT '账号查询',
    INDEX `idx_audit_login_log_user_time` (`user_id`, `user_type`, `create_time`) COMMENT '用户查询',
    INDEX `idx_audit_login_log_result_time` (`login_result`, `create_time`) COMMENT '结果查询',
    INDEX `idx_audit_login_log_time` (`create_time`) COMMENT '时间查询',
    INDEX `idx_audit_login_log_tenant_time` (`tenant_id`, `create_time`) COMMENT '租户查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='审计-登录日志表';

-- =====================================================================
-- Dict 字典模块
-- =====================================================================

DROP TABLE IF EXISTS `dict_type`;
CREATE TABLE `dict_type`
(
    `id`                     bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `dict_type_code`         varchar(100) NOT NULL COMMENT '字典类型编码：全局唯一',
    `dict_type_name`         varchar(128) NOT NULL COMMENT '字典类型名称',
    `status`                 tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0-正常；1-停用',
    `description`            varchar(500)          DEFAULT NULL COMMENT '描述',

    `is_deleted`             tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
    `version`                int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`              bigint                DEFAULT NULL COMMENT '创建人ID',
    `create_time`            datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`              bigint                DEFAULT NULL COMMENT '更新人ID',
    `update_time`            datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    `active_dict_type_code`  varchar(100)
                             GENERATED ALWAYS AS (IF(`is_deleted` = 0, `dict_type_code`, NULL)) STORED
        COMMENT '有效字典类型编码；逻辑删除下唯一约束辅助列，is_deleted=0 时为 dict_type_code，否则为 NULL',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dict_type_active_code` (`active_dict_type_code`) COMMENT '有效字典类型编码全局唯一',
    INDEX `idx_dict_type_status` (`status`) COMMENT '状态查询',
    INDEX `idx_dict_type_deleted` (`is_deleted`) COMMENT '逻辑删除查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='字典-字典类型表';

DROP TABLE IF EXISTS `dict_item`;
CREATE TABLE `dict_item`
(
    `id`                     bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `dict_type_code`         varchar(100) NOT NULL COMMENT '字典类型编码：关联 dict_type.dict_type_code',
    `dict_label`             varchar(128) NOT NULL COMMENT '字典标签（展示用）',
    `dict_value`             varchar(100) NOT NULL COMMENT '字典键值（存储用）',
    `sort_order`             int          NOT NULL DEFAULT 0 COMMENT '显示顺序',
    `status`                 tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0-正常；1-停用',
    `color_type`             varchar(32)           DEFAULT NULL COMMENT '颜色类型：primary、success、info、warning、danger',
    `css_class`              varchar(100)          DEFAULT NULL COMMENT 'CSS样式类名',
    `description`            varchar(500)          DEFAULT NULL COMMENT '描述',

    `is_deleted`             tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
    `version`                int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`              bigint                DEFAULT NULL COMMENT '创建人ID',
    `create_time`            datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`              bigint                DEFAULT NULL COMMENT '更新人ID',
    `update_time`            datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    `active_dict_item_key` varchar(255)
                           GENERATED ALWAYS AS (
                               IF(`is_deleted` = 0, CONCAT(`dict_type_code`, '::', `dict_value`), NULL)
                               ) STORED
        COMMENT '有效字典项键；逻辑删除下唯一约束辅助列，is_deleted=0 时为 dict_type_code::dict_value，否则为 NULL',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dict_item_active_key` (`active_dict_item_key`) COMMENT '有效字典项键全局唯一',
    INDEX `idx_dict_item_type_sort` (`dict_type_code`, `sort_order`) COMMENT '字典类型排序查询',
    INDEX `idx_dict_item_status` (`status`) COMMENT '状态查询',
    INDEX `idx_dict_item_deleted` (`is_deleted`) COMMENT '逻辑删除查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='字典-字典数据表';

-- =====================================================================
-- OAuth 模块
-- =====================================================================

DROP TABLE IF EXISTS `oauth_client`;
CREATE TABLE `oauth_client`
(
    `id`                        bigint        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `client_id`                 varchar(128)  NOT NULL COMMENT '客户端编号：租户内唯一',
    `client_secret_hash`        varchar(255)  NOT NULL COMMENT '客户端密钥哈希',
    `client_name`               varchar(128)  NOT NULL COMMENT '应用名',
    `logo_url`                  varchar(512)           DEFAULT NULL COMMENT '应用图标 URL',
    `description`               varchar(512)           DEFAULT NULL COMMENT '应用描述',
    `status`                    tinyint       NOT NULL DEFAULT 0 COMMENT '状态：0-启用；1-禁用',
    `access_token_ttl_seconds`  int           NOT NULL COMMENT '访问令牌有效期（秒）',
    `refresh_token_ttl_seconds` int           NOT NULL COMMENT '刷新令牌有效期（秒）',
    `redirect_uris`             json          NOT NULL COMMENT '可重定向的 URI 地址数组',
    `authorized_grant_types`    json          NOT NULL COMMENT '授权类型数组：password、authorization_code、implicit、refresh_token、client_credentials',
    `scopes`                    json                   DEFAULT NULL COMMENT '授权范围数组',
    `auto_approve_scopes`       json                   DEFAULT NULL COMMENT '自动通过的授权范围数组',
    `authorities`               json                   DEFAULT NULL COMMENT '权限数组',
    `resource_ids`              json                   DEFAULT NULL COMMENT '资源ID数组',
    `additional_information`    json                   DEFAULT NULL COMMENT '附加信息',

    `tenant_id`                 bigint        NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `is_deleted`                tinyint       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
    `version`                   int           NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`                 bigint                 DEFAULT NULL COMMENT '创建人ID',
    `create_time`               datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`                 bigint                 DEFAULT NULL COMMENT '更新人ID',
    `update_time`               datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    `active_client_id`          varchar(128)
                                GENERATED ALWAYS AS (IF(`is_deleted` = 0, `client_id`, NULL)) STORED
        COMMENT '有效客户端编号；逻辑删除下唯一约束辅助列，is_deleted=0 时为 client_id，否则为 NULL',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_oauth_client_tenant_active_client_id` (`tenant_id`, `active_client_id`) COMMENT '租户内有效客户端编号唯一',
    INDEX `idx_oauth_client_tenant_status` (`tenant_id`, `status`) COMMENT '状态查询',
    INDEX `idx_oauth_client_tenant_deleted` (`tenant_id`, `is_deleted`) COMMENT '逻辑删除查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='OAuth-客户端配置表';

DROP TABLE IF EXISTS `oauth_code`;
CREATE TABLE `oauth_code`
(
    `id`                      bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `authorization_code_hash` varchar(128) NOT NULL COMMENT '授权码哈希（SHA-256）',
    `client_id`               varchar(128) NOT NULL COMMENT '客户端编号',
    `user_id`                 bigint       NOT NULL COMMENT '用户ID',
    `user_type`               tinyint      NOT NULL COMMENT '用户类型：1-会员；2-管理员',
    `scopes`                  json                  DEFAULT NULL COMMENT '授权范围数组',
    `redirect_uri`            varchar(512)          DEFAULT NULL COMMENT '重定向 URI',
    `state`                   varchar(255) DEFAULT NULL COMMENT '客户端状态参数：原样返回',
    `is_used`                 tinyint      NOT NULL DEFAULT 0 COMMENT '是否已使用：0-未使用；1-已使用',
    `use_time`                datetime              DEFAULT NULL COMMENT '使用时间',
    `expire_time`             datetime     NOT NULL COMMENT '过期时间',
    `tenant_id`               bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `create_time`             datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_oauth_code_hash` (`authorization_code_hash`) COMMENT '授权码哈希唯一',
    INDEX `idx_oauth_code_expire` (`expire_time`) COMMENT '过期查询',
    INDEX `idx_oauth_code_user` (`user_id`, `user_type`) COMMENT '用户查询',
    INDEX `idx_oauth_code_tenant` (`tenant_id`) COMMENT '租户查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='OAuth-授权码表';

DROP TABLE IF EXISTS `oauth_refresh_token`;
CREATE TABLE `oauth_refresh_token`
(
    `id`                 bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `refresh_token_hash` varchar(128) NOT NULL COMMENT '刷新令牌哈希（SHA-256）',
    `client_id`          varchar(128) NOT NULL COMMENT '客户端编号',
    `user_id`            bigint       NOT NULL COMMENT '用户ID',
    `user_type`          tinyint      NOT NULL COMMENT '用户类型：1-会员；2-管理员',
    `scopes`             json                  DEFAULT NULL COMMENT '授权范围数组',
    `is_revoked`         tinyint      NOT NULL DEFAULT 0 COMMENT '是否已撤销：0-否；1-是',
    `revoke_time`        datetime              DEFAULT NULL COMMENT '撤销时间',
    `expire_time`        datetime     NOT NULL COMMENT '过期时间',
    `tenant_id`          bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `create_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_oauth_refresh_token_hash` (`refresh_token_hash`) COMMENT '刷新令牌哈希唯一',
    INDEX `idx_oauth_refresh_token_user` (`user_id`, `user_type`) COMMENT '用户查询',
    INDEX `idx_oauth_refresh_token_expire` (`expire_time`) COMMENT '过期查询',
    INDEX `idx_oauth_refresh_token_tenant` (`tenant_id`) COMMENT '租户查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='OAuth-刷新令牌表';

DROP TABLE IF EXISTS `oauth_access_token`;
CREATE TABLE `oauth_access_token`
(
    `id`                  bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `access_token_hash`   varchar(128) NOT NULL COMMENT '访问令牌哈希（SHA-256）',
    `refresh_token_hash`  varchar(128)          DEFAULT NULL COMMENT '刷新令牌哈希（SHA-256）',
    `client_id`           varchar(128) NOT NULL COMMENT '客户端编号',
    `user_id`             bigint       NOT NULL COMMENT '用户ID',
    `user_type`           tinyint      NOT NULL COMMENT '用户类型：1-会员；2-管理员',
    `user_info`           json                  DEFAULT NULL COMMENT '用户信息快照',
    `scopes`              json                  DEFAULT NULL COMMENT '授权范围数组',
    `is_revoked`          tinyint      NOT NULL DEFAULT 0 COMMENT '是否已撤销：0-否；1-是',
    `revoke_time`         datetime              DEFAULT NULL COMMENT '撤销时间',
    `expire_time`         datetime     NOT NULL COMMENT '过期时间',
    `tenant_id`           bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `create_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_oauth_access_token_hash` (`access_token_hash`) COMMENT '访问令牌哈希唯一',
    INDEX `idx_oauth_access_token_refresh_hash` (`refresh_token_hash`) COMMENT '刷新令牌哈希查询',
    INDEX `idx_oauth_access_token_user` (`user_id`, `user_type`) COMMENT '用户查询',
    INDEX `idx_oauth_access_token_client` (`client_id`) COMMENT '客户端查询',
    INDEX `idx_oauth_access_token_expire` (`expire_time`) COMMENT '过期查询',
    INDEX `idx_oauth_access_token_tenant` (`tenant_id`) COMMENT '租户查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='OAuth-访问令牌表';

DROP TABLE IF EXISTS `oauth_consent`;
CREATE TABLE `oauth_consent`
(
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `client_id`   varchar(128) NOT NULL COMMENT '客户端编号',
    `user_id`     bigint       NOT NULL COMMENT '用户ID',
    `user_type`   tinyint      NOT NULL COMMENT '用户类型：1-会员；2-管理员',
    `scopes`      json         NOT NULL COMMENT '已批准的授权范围数组',
    `is_approved` tinyint      NOT NULL DEFAULT 0 COMMENT '是否已批准：0-未批准；1-已批准',
    `expire_time` datetime     NOT NULL COMMENT '过期时间',

    `tenant_id`   bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `version`     int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`   bigint                DEFAULT NULL COMMENT '创建人ID',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   bigint                DEFAULT NULL COMMENT '更新人ID',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_oauth_consent_tenant_client_user` (`tenant_id`, `client_id`, `user_id`, `user_type`) COMMENT '租户内客户端用户同意唯一',
    INDEX `idx_oauth_consent_user` (`user_id`, `user_type`) COMMENT '用户查询',
    INDEX `idx_oauth_consent_expire` (`expire_time`) COMMENT '过期查询',
    INDEX `idx_oauth_consent_tenant` (`tenant_id`) COMMENT '租户查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='OAuth-授权同意表';

-- =====================================================================
-- End of Realm DB Schema v1.0.0 · CN Edition (MySQL 5.7)
-- =====================================================================
