-- =====================================================================
-- Realm DB Schema v3.0.0 · CN Edition (MySQL 5.7.30-log) · OneID Edition
-- =====================================================================
-- 设计理念：OneID + OneMeta + OneService
-- 实现范式：读时聚合 + 写时路由 + 轻量级不可变映射（逻辑统一，物理隔离）
-- 新增表：iam_user_identity / iam_user_credential / iam_user_profile
--          iam_user_client_binding / iam_user_device / iam_user_session
-- 重构表：iam_user（拆分为 7 张 IAM 表）
-- 总表数：50 张
-- =====================================================================
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =====================================================================
-- IAM 身份权限模块（OneID 核心）
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. iam_user - OneID 根锚点
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `iam_user`;
CREATE TABLE `iam_user`
(
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID（用户ID）：一个自然人唯一',
    `user_no`     varchar(64)  NOT NULL COMMENT '用户编号：业务可见全局唯一，如 U000000000001',
    `user_type`   varchar(20)  NOT NULL COMMENT '用户归属域：INTERNAL-内部员工；EXTERNAL-外部会员（物理路由锚点）',
    `nickname`    varchar(64)  NOT NULL COMMENT '用户昵称',
    `status`      varchar(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-正常；DISABLED-禁用；FROZEN-冻结；PENDING-未激活；CANCELLED-已注销',

    `tenant_id`   bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `is_deleted`  tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
    `version`     int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`   bigint                DEFAULT NULL COMMENT '创建人ID',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   bigint                DEFAULT NULL COMMENT '更新人ID',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    `active_user_no` varchar(64)
        GENERATED ALWAYS AS (IF(`is_deleted` = 0, `user_no`, NULL)) STORED
        COMMENT '有效用户编号；用于唯一约束',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_iam_user_tenant_no` (`tenant_id`, `active_user_no`) COMMENT '租户内有效用户编号唯一',
    INDEX `idx_iam_user_tenant_type_status` (`tenant_id`, `user_type`, `status`) COMMENT '归属域+状态查询（路由键）',
    INDEX `idx_iam_user_tenant_time`        (`tenant_id`, `create_time`)          COMMENT '时间查询',
    INDEX `idx_iam_user_tenant_deleted`     (`tenant_id`, `is_deleted`)           COMMENT '逻辑删除查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='IAM-用户表（OneID 根锚点）';

-- ---------------------------------------------------------------------
-- 2. iam_user_identity - 登录标识表（OneID 灵魂表）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `iam_user_identity`;
CREATE TABLE `iam_user_identity`
(
    `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`        bigint       NOT NULL COMMENT '用户ID：关联 iam_user.id',
    `identity_type`  varchar(32)  NOT NULL COMMENT '标识类型：USERNAME/EMAIL/PHONE/WECHAT/QQ/APPLE/GOOGLE/GITHUB/OIDC/DINGTALK/FEISHU',
    `identity_value` varchar(255) NOT NULL COMMENT '标识值：如用户名、邮箱、手机号、第三方openid',
    `is_verified`    tinyint      NOT NULL DEFAULT 0 COMMENT '是否已验证：0-否；1-是',
    `verified_time`  datetime              DEFAULT NULL COMMENT '验证时间',
    `is_primary`     tinyint      NOT NULL DEFAULT 0 COMMENT '是否主标识：0-否；1-是（同类型下唯一）',
    `extra_data`     json                  DEFAULT NULL COMMENT '扩展数据：如第三方昵称、头像',

    `tenant_id`      bigint       NOT NULL DEFAULT 0 COMMENT '租户ID',
    `is_deleted`     tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `version`        int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`      bigint                DEFAULT NULL COMMENT '创建人ID',
    `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      bigint                DEFAULT NULL COMMENT '更新人ID',
    `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    `active_identity_value` varchar(255)
        GENERATED ALWAYS AS (IF(`is_deleted` = 0, `identity_value`, NULL)) STORED
        COMMENT '有效标识值；用于唯一约束',
    `primary_marker` tinyint
        GENERATED ALWAYS AS (IF(`is_deleted` = 0 AND `is_primary` = 1, 1, NULL)) STORED
        COMMENT '主标识标记；保证同用户同类型下只有一个主标识',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_iam_user_identity_value`   (`tenant_id`, `identity_type`, `active_identity_value`) COMMENT '租户内同类型标识唯一',
    UNIQUE KEY `uk_iam_user_identity_primary` (`tenant_id`, `user_id`, `identity_type`, `primary_marker`) COMMENT '同用户同类型主标识唯一',
    INDEX `idx_iam_user_identity_user`        (`tenant_id`, `user_id`)                             COMMENT '按用户查询',
    INDEX `idx_iam_user_identity_type`        (`tenant_id`, `identity_type`)                       COMMENT '按类型查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='IAM-用户身份标识表（OneID 核心）';

-- ---------------------------------------------------------------------
-- 3. iam_user_credential - 凭证表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `iam_user_credential`;
CREATE TABLE `iam_user_credential`
(
    `id`               bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`          bigint       NOT NULL COMMENT '用户ID：关联 iam_user.id',
    `credential_type`  varchar(32)  NOT NULL COMMENT '凭证类型：PASSWORD/TOTP/PASSKEY/CERT/SMS_CODE',
    `credential_data`  varchar(512) NOT NULL COMMENT '凭证数据：密码哈希/TOTP密钥/公钥',
    `algorithm`        varchar(32)           DEFAULT NULL COMMENT '算法：bcrypt/argon2/RS256/HMAC-SHA1',
    `expire_time`      datetime              DEFAULT NULL COMMENT '过期时间：NULL 表示永不过期',
    `last_used_time`   datetime              DEFAULT NULL COMMENT '最后使用时间',

    `tenant_id`        bigint       NOT NULL DEFAULT 0 COMMENT '租户ID',
    `is_deleted`       tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `version`          int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`        bigint                DEFAULT NULL COMMENT '创建人ID',
    `create_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        bigint                DEFAULT NULL COMMENT '更新人ID',
    `update_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    `active_credential_type` varchar(32)
        GENERATED ALWAYS AS (IF(`is_deleted` = 0, `credential_type`, NULL)) STORED
        COMMENT '有效凭证类型',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_iam_user_credential` (`tenant_id`, `user_id`, `active_credential_type`) COMMENT '同用户同凭证类型唯一',
    INDEX `idx_iam_user_credential_user` (`tenant_id`, `user_id`) COMMENT '按用户查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='IAM-用户凭证表';

-- ---------------------------------------------------------------------
-- 4. iam_user_profile - 扩展属性表（OneMeta）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `iam_user_profile`;
CREATE TABLE `iam_user_profile`
(
    `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`        bigint       NOT NULL COMMENT '用户ID：关联 iam_user.id',
    `profile_key`    varchar(64)  NOT NULL COMMENT '属性键：realName/avatarUrl/departmentId/gender/level/points',
    `profile_value`  text         NOT NULL COMMENT '属性值',
    `profile_type`   varchar(32)  NOT NULL DEFAULT 'STRING' COMMENT '值类型：STRING/NUMBER/JSON/DATE/BOOL',

    `tenant_id`      bigint       NOT NULL DEFAULT 0 COMMENT '租户ID',
    `is_deleted`     tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `version`        int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`      bigint                DEFAULT NULL COMMENT '创建人ID',
    `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      bigint                DEFAULT NULL COMMENT '更新人ID',
    `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    `active_profile_key` varchar(64)
        GENERATED ALWAYS AS (IF(`is_deleted` = 0, `profile_key`, NULL)) STORED
        COMMENT '有效属性键',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_iam_user_profile` (`tenant_id`, `user_id`, `active_profile_key`) COMMENT '同用户同属性键唯一',
    INDEX `idx_iam_user_profile_user` (`tenant_id`, `user_id`) COMMENT '按用户查询',
    INDEX `idx_iam_user_profile_key`  (`tenant_id`, `profile_key`) COMMENT '按属性键查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='IAM-用户扩展属性表（OneMeta）';

-- ---------------------------------------------------------------------
-- 5. iam_user_client_binding - 端绑定表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `iam_user_client_binding`;
CREATE TABLE `iam_user_client_binding`
(
    `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`           bigint       NOT NULL COMMENT '用户ID：关联 iam_user.id',
    `client_type`       tinyint      NOT NULL COMMENT '端类型：1-货主端；2-车主端；3-平台端；4-管理后台',
    `platform_type`     tinyint      NOT NULL COMMENT '平台类型：1-iOS；2-Android；3-微信小程序；4-百度小程序；5-支付宝小程序；6-H5；7-Web；8-HarmonyOS',
    `external_id`       varchar(128)          DEFAULT NULL COMMENT '外部ID：如小程序openid、推送token',
    `bind_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
    `last_active_time`  datetime              DEFAULT NULL COMMENT '最后活跃时间',

    `tenant_id`         bigint       NOT NULL DEFAULT 0 COMMENT '租户ID',
    `is_deleted`        tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    `active_binding_key` varchar(200)
        GENERATED ALWAYS AS (
                            IF(`is_deleted` = 0,
                               CONCAT(`client_type`, '::', `platform_type`, '::', COALESCE(`external_id`, '')),
                               NULL)
                            ) STORED COMMENT '有效绑定键',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_iam_user_client_binding` (`tenant_id`, `user_id`, `active_binding_key`) COMMENT '同用户同端同平台唯一',
    INDEX `idx_iam_user_client_binding_lookup` (`tenant_id`, `client_type`, `platform_type`, `external_id`) COMMENT '按端+平台+外部ID查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='IAM-用户端绑定表';

-- ---------------------------------------------------------------------
-- 6. iam_user_device - 设备表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `iam_user_device`;
CREATE TABLE `iam_user_device`
(
    `id`                  bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`             bigint       NOT NULL COMMENT '用户ID：关联 iam_user.id',
    `device_fingerprint`  varchar(128) NOT NULL COMMENT '设备指纹',
    `device_name`         varchar(128)          DEFAULT NULL COMMENT '设备名称',
    `platform_type`       tinyint      NOT NULL COMMENT '平台类型',
    `trust_level`         tinyint      NOT NULL DEFAULT 0 COMMENT '信任等级：0-未知；1-可信；2-可疑；3-黑名单',
    `last_ip`             varchar(45)           DEFAULT NULL COMMENT '最后登录IP',
    `last_active_time`    datetime              DEFAULT NULL COMMENT '最后活跃时间',

    `tenant_id`           bigint       NOT NULL DEFAULT 0 COMMENT '租户ID',
    `is_deleted`          tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    `active_fingerprint` varchar(128)
                          GENERATED ALWAYS AS (IF(`is_deleted` = 0, `device_fingerprint`, NULL)) STORED
        COMMENT '有效设备指纹',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_iam_user_device` (`tenant_id`, `user_id`, `active_fingerprint`) COMMENT '同用户同设备唯一',
    INDEX `idx_iam_user_device_fingerprint` (`tenant_id`, `device_fingerprint`) COMMENT '按设备指纹查询',
    INDEX `idx_iam_user_device_trust`       (`tenant_id`, `trust_level`)        COMMENT '按信任等级查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='IAM-用户设备表';

-- ---------------------------------------------------------------------
-- 7. iam_user_session - 会话表（情境感知）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `iam_user_session`;
CREATE TABLE `iam_user_session`
(
    `id`               bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `session_id`       varchar(64)  NOT NULL COMMENT '会话ID',
    `user_id`          bigint       NOT NULL COMMENT '用户ID',
    `user_type`        varchar(20)  NOT NULL COMMENT '用户类型（快照）',
    `client_type`      tinyint               DEFAULT NULL COMMENT '端类型',
    `platform_type`    tinyint               DEFAULT NULL COMMENT '平台类型',
    `device_id`        bigint                DEFAULT NULL COMMENT '设备ID：关联 iam_user_device.id',
    `auth_type`        varchar(32)  NOT NULL COMMENT '登录方式：PASSWORD/SMS/WECHAT/GOOGLE/GITHUB/OIDC',
    `ip_address`       varchar(45)           DEFAULT NULL COMMENT '登录IP',
    `location`         varchar(128)          DEFAULT NULL COMMENT 'IP解析位置',
    `network_type`     varchar(32)           DEFAULT NULL COMMENT '网络类型：WIFI/4G/5G/UNKNOWN',
    `risk_score`       int          NOT NULL DEFAULT 0 COMMENT '风险评分：0-100',
    `login_time`       datetime     NOT NULL COMMENT '登录时间',
    `last_active_time` datetime              DEFAULT NULL COMMENT '最后活跃时间',
    `expire_time`      datetime     NOT NULL COMMENT '过期时间',

    `tenant_id`        bigint       NOT NULL DEFAULT 0 COMMENT '租户ID',
    `create_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_iam_user_session_id`   (`session_id`)                          COMMENT '会话ID唯一',
    INDEX `idx_iam_user_session_user`     (`tenant_id`, `user_id`, `expire_time`) COMMENT '按用户查询',
    INDEX `idx_iam_user_session_cleanup`  (`expire_time`)                         COMMENT '过期清理',
    INDEX `idx_iam_user_session_risk`     (`tenant_id`, `risk_score`)             COMMENT '风险查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='IAM-用户会话表（情境感知）';

-- ---------------------------------------------------------------------
-- 8. iam_role - 角色表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `iam_role`;
CREATE TABLE `iam_role`
(
    `id`               bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID（角色ID）',
    `role_name`        varchar(128) NOT NULL COMMENT '角色名称',
    `role_code`        varchar(100) NOT NULL COMMENT '角色编码：租户内唯一',
    `role_category`    varchar(32)  NOT NULL DEFAULT 'BUSINESS' COMMENT '角色分类：BUSINESS-业务角色；SYSTEM-系统角色；DATA-数据角色',
    `sort_order`       int          NOT NULL DEFAULT 0 COMMENT '显示顺序',
    `data_scope`       tinyint      NOT NULL DEFAULT 5 COMMENT '数据范围：1-全部；2-自定义；3-本部门；4-本部门及以下；5-仅本人',
    `status`           tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0-正常；1-停用',
    `description`      varchar(500)          DEFAULT NULL COMMENT '描述',

    `tenant_id`        bigint       NOT NULL DEFAULT 0 COMMENT '租户ID',
    `is_deleted`       tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `version`          int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`        bigint                DEFAULT NULL COMMENT '创建人ID',
    `create_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        bigint                DEFAULT NULL COMMENT '更新人ID',
    `update_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    `active_role_code` varchar(100)
                       GENERATED ALWAYS AS (IF(`is_deleted` = 0, `role_code`, NULL)) STORED COMMENT '有效角色编码',
    `active_role_name` varchar(128)
                       GENERATED ALWAYS AS (IF(`is_deleted` = 0, `role_name`, NULL)) STORED COMMENT '有效角色名称',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_iam_role_tenant_active_role_code` (`tenant_id`, `active_role_code`) COMMENT '租户内有效角色编码唯一',
    UNIQUE KEY `uk_iam_role_tenant_active_role_name` (`tenant_id`, `active_role_name`) COMMENT '租户内有效角色名称唯一',
    INDEX `idx_iam_role_tenant_deleted_status` (`tenant_id`, `is_deleted`, `status`) COMMENT '逻辑删除状态查询',
    INDEX `idx_iam_role_tenant_category`       (`tenant_id`, `role_category`)        COMMENT '角色分类查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='IAM-角色表';

-- ---------------------------------------------------------------------
-- 9. iam_role_department - 角色部门关联表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `iam_role_department`;
CREATE TABLE `iam_role_department`
(
    `id`            bigint   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `role_id`       bigint   NOT NULL COMMENT '角色ID：关联 iam_role.id',
    `department_id` bigint   NOT NULL COMMENT '部门ID：关联 iam_department.id',
    `tenant_id`     bigint   NOT NULL DEFAULT 0 COMMENT '租户ID',
    `create_by`     bigint            DEFAULT NULL COMMENT '创建人ID',
    `create_time`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_iam_role_dept_role_dept_tenant` (`role_id`, `department_id`, `tenant_id`) COMMENT '角色部门租户唯一',
    INDEX `idx_iam_role_dept_dept`   (`department_id`) COMMENT '部门查询',
    INDEX `idx_iam_role_dept_tenant` (`tenant_id`)     COMMENT '租户查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='IAM-角色与部门关联表';

-- ---------------------------------------------------------------------
-- 10. iam_menu - 菜单权限表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `iam_menu`;
CREATE TABLE `iam_menu`
(
    `id`                   bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID（菜单ID）',
    `menu_name`            varchar(128) NOT NULL COMMENT '菜单名称',
    `permission_code`      varchar(100)          DEFAULT NULL COMMENT '权限标识：如 system:user:query',
    `menu_type`            tinyint      NOT NULL COMMENT '菜单类型：1-目录；2-菜单；3-按钮',
    `sort_order`           int          NOT NULL DEFAULT 0 COMMENT '显示顺序',
    `parent_id`            bigint       NOT NULL DEFAULT 0 COMMENT '父级ID：0-顶级',
    `ancestors`            varchar(500)          DEFAULT NULL COMMENT '祖级列表，逗号分隔',
    `path`                 varchar(200)          DEFAULT NULL COMMENT '路由地址',
    `icon`                 varchar(100)          DEFAULT NULL COMMENT '菜单图标',
    `component`            varchar(255)          DEFAULT NULL COMMENT '前端组件路径',
    `component_name`       varchar(255)          DEFAULT NULL COMMENT '前端组件名',
    `status`               tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0-正常；1-停用',
    `is_visible`           tinyint      NOT NULL DEFAULT 1 COMMENT '是否可见：0-否；1-是',
    `is_keep_alive`        tinyint      NOT NULL DEFAULT 1 COMMENT '是否缓存：0-否；1-是',
    `is_always_show`       tinyint      NOT NULL DEFAULT 1 COMMENT '是否总是显示：0-否；1-是',

    `tenant_id`            bigint       NOT NULL DEFAULT 0 COMMENT '租户ID',
    `is_deleted`           tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `version`              int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`            bigint                DEFAULT NULL COMMENT '创建人ID',
    `create_time`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`            bigint                DEFAULT NULL COMMENT '更新人ID',
    `update_time`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    `active_permission_code` varchar(100)
        GENERATED ALWAYS AS (IF(`is_deleted` = 0, `permission_code`, NULL)) STORED COMMENT '有效权限标识',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_iam_menu_tenant_active_permission_code` (`tenant_id`, `active_permission_code`) COMMENT '租户内有效权限标识唯一',
    INDEX `idx_iam_menu_tenant_parent_sort` (`tenant_id`, `parent_id`, `sort_order`) COMMENT '父级排序查询',
    INDEX `idx_iam_menu_tenant_deleted_type_status` (`tenant_id`, `is_deleted`, `menu_type`, `status`) COMMENT '逻辑删除类型状态查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='IAM-菜单权限表';

-- ---------------------------------------------------------------------
-- 11. iam_position - 岗位表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `iam_position`;
CREATE TABLE `iam_position`
(
    `id`                   bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID（岗位ID）',
    `position_code`        varchar(64)  NOT NULL COMMENT '岗位编码：租户内唯一',
    `position_name`        varchar(128) NOT NULL COMMENT '岗位名称',
    `sort_order`           int          NOT NULL DEFAULT 0 COMMENT '显示顺序',
    `status`               tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0-正常；1-停用',
    `description`          varchar(500)          DEFAULT NULL COMMENT '描述',

    `tenant_id`            bigint       NOT NULL DEFAULT 0 COMMENT '租户ID',
    `is_deleted`           tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `version`              int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`            bigint                DEFAULT NULL COMMENT '创建人ID',
    `create_time`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`            bigint                DEFAULT NULL COMMENT '更新人ID',
    `update_time`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    `active_position_code` varchar(64)
                           GENERATED ALWAYS AS (IF(`is_deleted` = 0, `position_code`, NULL)) STORED COMMENT '有效岗位编码',
    `active_position_name` varchar(128)
                           GENERATED ALWAYS AS (IF(`is_deleted` = 0, `position_name`, NULL)) STORED COMMENT '有效岗位名称',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_iam_position_tenant_active_position_code` (`tenant_id`, `active_position_code`) COMMENT '租户内有效岗位编码唯一',
    UNIQUE KEY `uk_iam_position_tenant_active_position_name` (`tenant_id`, `active_position_name`) COMMENT '租户内有效岗位名称唯一',
    INDEX `idx_iam_position_tenant_deleted_status` (`tenant_id`, `is_deleted`, `status`) COMMENT '逻辑删除状态查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='IAM-岗位表';

-- ---------------------------------------------------------------------
-- 12. iam_department - 部门表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `iam_department`;
CREATE TABLE `iam_department`
(
    `id`               bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID（部门ID）',
    `department_name`  varchar(128) NOT NULL COMMENT '部门名称',
    `parent_id`        bigint       NOT NULL DEFAULT 0 COMMENT '父级ID：0-顶级',
    `ancestors`        varchar(500)          DEFAULT NULL COMMENT '祖级列表，逗号分隔',
    `sort_order`       int          NOT NULL DEFAULT 0 COMMENT '显示顺序',
    `manager_user_id`  bigint                DEFAULT NULL COMMENT '部门负责人用户ID：关联 iam_user.id',
    `phone_number`     varchar(20)           DEFAULT NULL COMMENT '联系电话',
    `email`            varchar(254)          DEFAULT NULL COMMENT '邮箱',
    `status`           tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0-正常；1-停用',

    `tenant_id`        bigint       NOT NULL DEFAULT 0 COMMENT '租户ID',
    `is_deleted`       tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `version`          int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`        bigint                DEFAULT NULL COMMENT '创建人ID',
    `create_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        bigint                DEFAULT NULL COMMENT '更新人ID',
    `update_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    `active_department_name` varchar(128)
        GENERATED ALWAYS AS (IF(`is_deleted` = 0, `department_name`, NULL)) STORED COMMENT '有效部门名称',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_iam_department_tenant_parent_name` (`tenant_id`, `parent_id`, `active_department_name`) COMMENT '同父级下有效部门名称唯一',
    INDEX `idx_iam_department_tenant_parent_sort` (`tenant_id`, `parent_id`, `sort_order`) COMMENT '父级排序查询',
    INDEX `idx_iam_department_tenant_manager`     (`tenant_id`, `manager_user_id`)          COMMENT '负责人查询',
    INDEX `idx_iam_department_tenant_deleted_status` (`tenant_id`, `is_deleted`, `status`)  COMMENT '逻辑删除状态查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='IAM-部门表';

-- ---------------------------------------------------------------------
-- 13. iam_user_role - 用户角色关联表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `iam_user_role`;
CREATE TABLE `iam_user_role`
(
    `id`          bigint   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`     bigint   NOT NULL COMMENT '用户ID：关联 iam_user.id',
    `role_id`     bigint   NOT NULL COMMENT '角色ID：关联 iam_role.id',
    `tenant_id`   bigint   NOT NULL DEFAULT 0 COMMENT '租户ID',
    `create_by`   bigint            DEFAULT NULL COMMENT '创建人ID',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_iam_user_role_user_role_tenant` (`user_id`, `role_id`, `tenant_id`) COMMENT '用户角色租户唯一',
    INDEX `idx_iam_user_role_role`   (`role_id`)   COMMENT '角色查询',
    INDEX `idx_iam_user_role_tenant` (`tenant_id`) COMMENT '租户查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='IAM-用户角色关联表';

-- ---------------------------------------------------------------------
-- 14. iam_role_menu - 角色菜单关联表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `iam_role_menu`;
CREATE TABLE `iam_role_menu`
(
    `id`          bigint   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `role_id`     bigint   NOT NULL COMMENT '角色ID：关联 iam_role.id',
    `menu_id`     bigint   NOT NULL COMMENT '菜单ID：关联 iam_menu.id',
    `tenant_id`   bigint   NOT NULL DEFAULT 0 COMMENT '租户ID',
    `create_by`   bigint            DEFAULT NULL COMMENT '创建人ID',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_iam_role_menu_role_menu_tenant` (`role_id`, `menu_id`, `tenant_id`) COMMENT '角色菜单租户唯一',
    INDEX `idx_iam_role_menu_menu`   (`menu_id`)   COMMENT '菜单查询',
    INDEX `idx_iam_role_menu_tenant` (`tenant_id`) COMMENT '租户查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='IAM-角色菜单关联表';

-- ---------------------------------------------------------------------
-- 15. iam_user_position - 用户岗位关联表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `iam_user_position`;
CREATE TABLE `iam_user_position`
(
    `id`          bigint   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`     bigint   NOT NULL COMMENT '用户ID：关联 iam_user.id',
    `position_id` bigint   NOT NULL COMMENT '岗位ID：关联 iam_position.id',
    `tenant_id`   bigint   NOT NULL DEFAULT 0 COMMENT '租户ID',
    `create_by`   bigint            DEFAULT NULL COMMENT '创建人ID',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_iam_user_position_user_position_tenant` (`user_id`, `position_id`, `tenant_id`) COMMENT '用户岗位租户唯一',
    INDEX `idx_iam_user_position_position` (`position_id`) COMMENT '岗位查询',
    INDEX `idx_iam_user_position_tenant`   (`tenant_id`)   COMMENT '租户查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='IAM-用户岗位关联表';

-- =====================================================================
-- Infra 基础设施模块（与 v2.0.0 相同）
-- =====================================================================

DROP TABLE IF EXISTS `infra_config`;
CREATE TABLE `infra_config`
(
    `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `category`          varchar(64)  NOT NULL COMMENT '参数分组：如 biz、url、system',
    `config_type`       tinyint      NOT NULL COMMENT '参数类型：1-系统内置；2-自定义',
    `config_name`       varchar(128) NOT NULL COMMENT '参数名称',
    `config_key`        varchar(100) NOT NULL COMMENT '参数键名：租户内唯一',
    `config_value`      text         NOT NULL COMMENT '参数键值',
    `is_visible`        tinyint      NOT NULL DEFAULT 1 COMMENT '是否可见：0-否；1-是',
    `description`       varchar(500)          DEFAULT NULL COMMENT '描述',
    `tenant_id`         bigint       NOT NULL DEFAULT 0 COMMENT '租户ID',
    `is_deleted`        tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `version`           int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`         bigint                DEFAULT NULL COMMENT '创建人ID',
    `create_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`         bigint                DEFAULT NULL COMMENT '更新人ID',
    `update_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `active_config_key` varchar(100) GENERATED ALWAYS AS (IF(`is_deleted` = 0, `config_key`, NULL)) STORED COMMENT '有效参数键名',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_infra_config_tenant_active_config_key` (`tenant_id`, `active_config_key`),
    INDEX `idx_infra_config_tenant_deleted_category` (`tenant_id`, `is_deleted`, `category`),
    INDEX `idx_infra_config_tenant_deleted_type`     (`tenant_id`, `is_deleted`, `config_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='基础设施-参数配置表';

DROP TABLE IF EXISTS `infra_job`;
CREATE TABLE `infra_job`
(
    `id`                  bigint        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `job_name`            varchar(128)  NOT NULL COMMENT '任务名称：租户内唯一',
    `job_status`          tinyint       NOT NULL DEFAULT 0 COMMENT '任务状态：0-初始化；1-运行中；2-暂停',
    `handler_name`        varchar(128)  NOT NULL COMMENT '任务处理器名称',
    `handler_params`      varchar(2048)          DEFAULT NULL COMMENT '任务处理器参数',
    `cron_expression`     varchar(64)   NOT NULL COMMENT 'CRON 表达式',
    `retry_count`         int           NOT NULL DEFAULT 0 COMMENT '重试次数',
    `retry_interval_ms`   int           NOT NULL DEFAULT 0 COMMENT '重试间隔（毫秒）',
    `monitor_timeout_ms`  int           NOT NULL DEFAULT 0 COMMENT '监控超时时间（毫秒）',
    `next_execution_time` datetime               DEFAULT NULL COMMENT '下次执行时间',
    `last_execution_time` datetime               DEFAULT NULL COMMENT '上次执行时间',
    `tenant_id`           bigint        NOT NULL DEFAULT 0 COMMENT '租户ID',
    `is_deleted`          tinyint       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `version`             int           NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`           bigint                 DEFAULT NULL COMMENT '创建人ID',
    `create_time`         datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`           bigint                 DEFAULT NULL COMMENT '更新人ID',
    `update_time`         datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `active_job_name`     varchar(128)  GENERATED ALWAYS AS (IF(`is_deleted` = 0, `job_name`, NULL)) STORED COMMENT '有效任务名称',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_infra_job_tenant_active_job_name` (`tenant_id`, `active_job_name`),
    INDEX `idx_infra_job_tenant_deleted_status` (`tenant_id`, `is_deleted`, `job_status`),
    INDEX `idx_infra_job_tenant_handler`        (`tenant_id`, `handler_name`),
    INDEX `idx_infra_job_next_exec_time`        (`next_execution_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='基础设施-定时任务表';

DROP TABLE IF EXISTS `infra_job_log`;
CREATE TABLE `infra_job_log`
(
    `id`               bigint        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `job_id`           bigint        NOT NULL COMMENT '定时任务ID',
    `handler_name`     varchar(128)  NOT NULL COMMENT '任务处理器名称（快照）',
    `handler_params`   varchar(2048)          DEFAULT NULL COMMENT '任务处理器参数（快照）',
    `execution_index`  int           NOT NULL DEFAULT 1 COMMENT '第几次执行',
    `start_time`       datetime      NOT NULL COMMENT '开始执行时间',
    `end_time`         datetime               DEFAULT NULL COMMENT '结束执行时间',
    `duration_ms`      int                    DEFAULT NULL COMMENT '执行时长（毫秒）',
    `execution_status` tinyint       NOT NULL COMMENT '执行状态：0-运行中；1-成功；2-失败',
    `result_data`      mediumtext             DEFAULT NULL COMMENT '结果数据',
    `tenant_id`        bigint        NOT NULL DEFAULT 0 COMMENT '租户ID',
    `create_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_infra_job_log_job_exec_index` (`job_id`, `execution_index`),
    INDEX `idx_infra_job_log_job_time`    (`job_id`, `create_time`),
    INDEX `idx_infra_job_log_status_time` (`execution_status`, `create_time`),
    INDEX `idx_infra_job_log_tenant_time` (`tenant_id`, `create_time`),
    INDEX `idx_infra_job_log_time`        (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='基础设施-定时任务执行日志表';

DROP TABLE IF EXISTS `infra_file_storage_config`;
CREATE TABLE `infra_file_storage_config`
(
    `id`                 bigint         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `config_name`        varchar(128)   NOT NULL COMMENT '配置名称：租户内唯一',
    `storage_type`       tinyint        NOT NULL COMMENT '存储类型：1-数据库；10-本地磁盘；11-FTP；12-SFTP；20-S3对象存储',
    `encrypted_config`   varbinary(8192) NOT NULL COMMENT '加密后的存储配置',
    `is_default`         tinyint        NOT NULL DEFAULT 0 COMMENT '是否默认：0-否；1-是',
    `default_marker`     tinyint        GENERATED ALWAYS AS (IF(`is_default` = 1 AND `is_deleted` = 0, 1, NULL)) STORED,
    `status`             tinyint        NOT NULL DEFAULT 0 COMMENT '状态：0-启用；1-禁用',
    `description`        varchar(255)            DEFAULT NULL COMMENT '描述',
    `tenant_id`          bigint         NOT NULL DEFAULT 0 COMMENT '租户ID',
    `is_deleted`         tinyint        NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `version`            int            NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`          bigint                  DEFAULT NULL COMMENT '创建人ID',
    `create_time`        datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`          bigint                  DEFAULT NULL COMMENT '更新人ID',
    `update_time`        datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `active_config_name` varchar(128)   GENERATED ALWAYS AS (IF(`is_deleted` = 0, `config_name`, NULL)) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_infra_file_storage_config_tenant_active_name` (`tenant_id`, `active_config_name`),
    UNIQUE KEY `uk_infra_file_storage_config_tenant_default`     (`tenant_id`, `default_marker`),
    INDEX `idx_infra_file_storage_config_tenant_deleted_type`   (`tenant_id`, `is_deleted`, `storage_type`),
    INDEX `idx_infra_file_storage_config_tenant_deleted_status` (`tenant_id`, `is_deleted`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='基础设施-文件存储配置表';

DROP TABLE IF EXISTS `infra_file_metadata`;
CREATE TABLE `infra_file_metadata`
(
    `id`                 bigint         NOT NULL AUTO_INCREMENT COMMENT '主键ID（文件ID）',
    `file_config_id`     bigint                  DEFAULT NULL COMMENT '文件配置ID',
    `original_name`      varchar(255)   NOT NULL COMMENT '原始文件名',
    `storage_path`       varchar(1024)  NOT NULL COMMENT '存储路径',
    `file_url`           varchar(2048)  NOT NULL COMMENT '文件访问URL',
    `content_type`       varchar(128)            DEFAULT NULL COMMENT '文件MIME类型',
    `size_bytes`         bigint         NOT NULL COMMENT '文件大小（字节）',
    `content_sha256`     varchar(64)             DEFAULT NULL COMMENT '文件内容SHA-256哈希',
    `reference_count`    int            NOT NULL DEFAULT 1 COMMENT '引用计数',
    `tenant_id`          bigint         NOT NULL DEFAULT 0 COMMENT '租户ID',
    `is_deleted`         tinyint        NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `version`            int            NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`          bigint                  DEFAULT NULL COMMENT '上传人ID',
    `create_time`        datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    `update_by`          bigint                  DEFAULT NULL COMMENT '更新人ID',
    `update_time`        datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `file_config_marker` bigint         GENERATED ALWAYS AS (COALESCE(`file_config_id`, 0)) STORED,
    `storage_path_hash`  char(64)       GENERATED ALWAYS AS (SHA2(`storage_path`, 256)) STORED,
    `active_path_hash`   char(64)       GENERATED ALWAYS AS (IF(`is_deleted` = 0, SHA2(`storage_path`, 256), NULL)) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_infra_file_metadata_tenant_config_pathhash` (`tenant_id`, `file_config_marker`, `active_path_hash`),
    INDEX `idx_infra_file_metadata_tenant_sha256`  (`tenant_id`, `content_sha256`),
    INDEX `idx_infra_file_metadata_tenant_time`    (`tenant_id`, `create_time`),
    INDEX `idx_infra_file_metadata_tenant_deleted` (`tenant_id`, `is_deleted`),
    INDEX `idx_infra_file_metadata_config_hash`    (`file_config_marker`, `storage_path_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='基础设施-文件元数据表';

DROP TABLE IF EXISTS `infra_file_content`;
CREATE TABLE `infra_file_content`
(
    `id`                bigint        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `file_config_id`    bigint        NOT NULL COMMENT '文件配置ID',
    `storage_path`      varchar(1024) NOT NULL COMMENT '文件路径',
    `content`           mediumblob    NOT NULL COMMENT '文件内容',
    `tenant_id`         bigint        NOT NULL DEFAULT 0 COMMENT '租户ID',
    `create_time`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `storage_path_hash` char(64)      GENERATED ALWAYS AS (SHA2(`storage_path`, 256)) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_infra_file_content_config_pathhash` (`file_config_id`, `storage_path_hash`),
    INDEX `idx_infra_file_content_tenant_time` (`tenant_id`, `create_time`),
    INDEX `idx_infra_file_content_time`        (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='基础设施-文件内容表';

DROP TABLE IF EXISTS `infra_data_source`;
CREATE TABLE `infra_data_source`
(
    `id`                          bigint          NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `data_source_name`            varchar(128)    NOT NULL COMMENT '数据源名称：租户内唯一',
    `database_type`               varchar(32)     NOT NULL COMMENT '数据库类型',
    `encrypted_jdbc_url`          varbinary(8192) NOT NULL COMMENT '加密后的 JDBC 连接串',
    `database_username`           varchar(255)    NOT NULL COMMENT '数据库用户名',
    `encrypted_database_password` varbinary(2048) NOT NULL COMMENT '加密后的数据库密码',
    `status`                      tinyint         NOT NULL DEFAULT 0 COMMENT '状态：0-启用；1-禁用',
    `description`                 varchar(500)             DEFAULT NULL COMMENT '描述',
    `tenant_id`                   bigint          NOT NULL DEFAULT 0 COMMENT '租户ID',
    `is_deleted`                  tinyint         NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `version`                     int             NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`                   bigint                   DEFAULT NULL COMMENT '创建人ID',
    `create_time`                 datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`                   bigint                   DEFAULT NULL COMMENT '更新人ID',
    `update_time`                 datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `active_data_source_name`     varchar(128)    GENERATED ALWAYS AS (IF(`is_deleted` = 0, `data_source_name`, NULL)) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_infra_data_source_tenant_active_name` (`tenant_id`, `active_data_source_name`),
    INDEX `idx_infra_data_source_tenant_deleted_type`   (`tenant_id`, `is_deleted`, `database_type`),
    INDEX `idx_infra_data_source_tenant_deleted_status` (`tenant_id`, `is_deleted`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='基础设施-数据源配置表';

DROP TABLE IF EXISTS `infra_api_access_log`;
CREATE TABLE `infra_api_access_log`
(
    `id`               bigint        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `trace_id`         varchar(64)   NOT NULL COMMENT '链路追踪ID',
    `user_id`          bigint        NOT NULL DEFAULT 0 COMMENT '用户ID',
    `user_type`        varchar(20)   NOT NULL DEFAULT 'EXTERNAL' COMMENT '用户类型',
    `application_name` varchar(128)  NOT NULL COMMENT '应用名',
    `ip_address`       varchar(45)   NOT NULL COMMENT '请求来源 IP',
    `user_agent`       varchar(1024) NOT NULL COMMENT '客户端 User-Agent',
    `http_method`      varchar(16)   NOT NULL COMMENT 'HTTP 方法',
    `request_url`      varchar(2048) NOT NULL COMMENT '请求 URL',
    `request_params`   mediumtext             DEFAULT NULL COMMENT '请求参数',
    `response_body`    mediumtext             DEFAULT NULL COMMENT '响应结果',
    `operation_module` varchar(64)            DEFAULT NULL COMMENT '操作模块',
    `operation_name`   varchar(64)            DEFAULT NULL COMMENT '操作名称',
    `operation_type`   tinyint                DEFAULT NULL COMMENT '操作类型',
    `start_time`       datetime      NOT NULL COMMENT '请求开始时间',
    `end_time`         datetime      NOT NULL COMMENT '请求结束时间',
    `duration_ms`      int           NOT NULL COMMENT '执行时长（毫秒）',
    `http_status_code` int           NOT NULL DEFAULT 0 COMMENT 'HTTP状态码',
    `result_code`      int           NOT NULL DEFAULT 0 COMMENT '业务结果码',
    `result_message`   varchar(512)           DEFAULT NULL COMMENT '业务结果提示',
    `tenant_id`        bigint        NOT NULL DEFAULT 0 COMMENT '租户ID',
    `create_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_infra_api_access_log_trace`             (`trace_id`),
    INDEX `idx_infra_api_access_log_tenant_user_time`  (`tenant_id`, `user_id`, `create_time`),
    INDEX `idx_infra_api_access_log_tenant_status_time`(`tenant_id`, `http_status_code`, `create_time`),
    INDEX `idx_infra_api_access_log_time`              (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='基础设施-API 访问日志表';

DROP TABLE IF EXISTS `infra_api_error_log`;
CREATE TABLE `infra_api_error_log`
(
    `id`                           bigint        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `trace_id`                     varchar(64)   NOT NULL COMMENT '链路追踪ID',
    `user_id`                      bigint        NOT NULL DEFAULT 0 COMMENT '用户ID',
    `user_type`                    varchar(20)   NOT NULL DEFAULT 'EXTERNAL' COMMENT '用户类型',
    `application_name`             varchar(128)  NOT NULL COMMENT '应用名',
    `ip_address`                   varchar(45)   NOT NULL COMMENT '请求来源 IP',
    `user_agent`                   varchar(1024) NOT NULL COMMENT '客户端 User-Agent',
    `http_method`                  varchar(16)   NOT NULL COMMENT 'HTTP 方法',
    `request_url`                  varchar(2048) NOT NULL COMMENT '请求 URL',
    `request_params`               mediumtext             DEFAULT NULL COMMENT '请求参数',
    `exception_name`               varchar(128)  NOT NULL COMMENT '异常类简称',
    `exception_message`            mediumtext    NOT NULL COMMENT '异常消息',
    `exception_root_cause_message` mediumtext    NOT NULL COMMENT '异常根因消息',
    `exception_stack_trace`        mediumtext    NOT NULL COMMENT '异常堆栈轨迹',
    `exception_class_name`         varchar(512)  NOT NULL COMMENT '异常发生的类全名',
    `exception_file_name`          varchar(512)  NOT NULL COMMENT '异常发生的源文件名',
    `exception_method_name`        varchar(512)  NOT NULL COMMENT '异常发生的方法名',
    `exception_line_number`        int           NOT NULL COMMENT '异常发生的源码行号',
    `exception_time`               datetime      NOT NULL COMMENT '异常发生时间',
    `process_status`               tinyint       NOT NULL DEFAULT 0 COMMENT '处理状态',
    `process_time`                 datetime               DEFAULT NULL COMMENT '处理时间',
    `process_by`                   bigint                 DEFAULT NULL COMMENT '处理人ID',
    `tenant_id`                    bigint        NOT NULL DEFAULT 0 COMMENT '租户ID',
    `create_time`                  datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`                  datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_infra_api_error_log_trace`             (`trace_id`),
    INDEX `idx_infra_api_error_log_status_time`       (`process_status`, `create_time`),
    INDEX `idx_infra_api_error_log_tenant_user_time`  (`tenant_id`, `user_id`, `create_time`),
    INDEX `idx_infra_api_error_log_exception_time`    (`exception_name`, `create_time`),
    INDEX `idx_infra_api_error_log_time`              (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='基础设施-API错误日志表';

DROP TABLE IF EXISTS `infra_codegen_table`;
CREATE TABLE `infra_codegen_table`
(
    `id`                    bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `data_source_id`        bigint       NOT NULL COMMENT '数据源配置ID',
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
    `frontend_type`         tinyint      NOT NULL COMMENT '前端类型',
    `parent_menu_id`        bigint                DEFAULT NULL COMMENT '父菜单ID',
    `master_table_id`       bigint                DEFAULT NULL COMMENT '主表ID',
    `sub_join_column_id`    bigint                DEFAULT NULL COMMENT '子表关联主表的字段ID',
    `is_sub_join_many`      tinyint      NOT NULL DEFAULT 0 COMMENT '主表与子表是否一对多',
    `tree_parent_column_id` bigint                DEFAULT NULL COMMENT '树表的父字段ID',
    `tree_name_column_id`   bigint                DEFAULT NULL COMMENT '树表的名字字段ID',
    `tenant_id`             bigint       NOT NULL DEFAULT 0 COMMENT '租户ID',
    `is_deleted`            tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `version`               int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`             bigint                DEFAULT NULL COMMENT '创建人ID',
    `create_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`             bigint                DEFAULT NULL COMMENT '更新人ID',
    `update_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `active_table_name`     varchar(200) GENERATED ALWAYS AS (IF(`is_deleted` = 0, `table_name`, NULL)) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_infra_codegen_table_tenant_source_name` (`tenant_id`, `data_source_id`, `active_table_name`),
    INDEX `idx_infra_codegen_table_tenant_menu`    (`tenant_id`, `parent_menu_id`),
    INDEX `idx_infra_codegen_table_tenant_master`  (`tenant_id`, `master_table_id`),
    INDEX `idx_infra_codegen_table_tenant_deleted` (`tenant_id`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='基础设施-代码生成表定义';

DROP TABLE IF EXISTS `infra_codegen_column`;
CREATE TABLE `infra_codegen_column`
(
    `id`                       bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `codegen_table_id`         bigint       NOT NULL COMMENT '代码生成表ID',
    `column_name`              varchar(200) NOT NULL COMMENT '数据库字段名',
    `data_type`                varchar(100) NOT NULL COMMENT '数据库字段类型',
    `column_comment`           varchar(500) NOT NULL COMMENT '数据库字段描述',
    `is_nullable`              tinyint      NOT NULL DEFAULT 0 COMMENT '是否允许为空',
    `is_primary_key`           tinyint      NOT NULL DEFAULT 0 COMMENT '是否主键',
    `ordinal_position`         int          NOT NULL COMMENT '字段序号',
    `java_type`                varchar(32)  NOT NULL COMMENT 'Java属性类型',
    `java_field`               varchar(64)  NOT NULL COMMENT 'Java属性名',
    `dict_type_code`           varchar(200)          DEFAULT NULL COMMENT '字典类型编码',
    `example_value`            varchar(64)           DEFAULT NULL COMMENT '数据示例',
    `is_create_operation`      tinyint      NOT NULL DEFAULT 1 COMMENT '是否为Create操作字段',
    `is_update_operation`      tinyint      NOT NULL DEFAULT 1 COMMENT '是否为Update操作字段',
    `is_list_operation`        tinyint      NOT NULL DEFAULT 1 COMMENT '是否为List查询字段',
    `list_operation_condition` varchar(32)  NOT NULL DEFAULT '=' COMMENT 'List查询条件类型',
    `is_list_operation_result` tinyint      NOT NULL DEFAULT 1 COMMENT '是否为List返回字段',
    `html_type`                varchar(32)  NOT NULL COMMENT '前端显示类型',
    `tenant_id`                bigint       NOT NULL DEFAULT 0 COMMENT '租户ID',
    `is_deleted`               tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `version`                  int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`                bigint                DEFAULT NULL COMMENT '创建人ID',
    `create_time`              datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`                bigint                DEFAULT NULL COMMENT '更新人ID',
    `update_time`              datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `active_column_name`       varchar(200) GENERATED ALWAYS AS (IF(`is_deleted` = 0, `column_name`, NULL)) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_infra_codegen_column_tenant_table_name` (`tenant_id`, `codegen_table_id`, `active_column_name`),
    INDEX `idx_infra_codegen_column_tenant_table_pos` (`tenant_id`, `codegen_table_id`, `ordinal_position`),
    INDEX `idx_infra_codegen_column_tenant_deleted`   (`tenant_id`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='基础设施-代码生成字段定义表';

-- =====================================================================
-- SaaS 租户模块
-- =====================================================================

DROP TABLE IF EXISTS `saas_tenant`;
CREATE TABLE `saas_tenant`
(
    `id`                   bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID（租户ID）',
    `tenant_code`          varchar(64)  NOT NULL COMMENT '租户编码：全局唯一',
    `tenant_name`          varchar(128) NOT NULL COMMENT '租户名称',
    `contact_user_id`      bigint                DEFAULT NULL COMMENT '联系人用户ID',
    `contact_name`         varchar(64)  NOT NULL COMMENT '联系人姓名',
    `contact_phone_number` varchar(20)           DEFAULT NULL COMMENT '联系人手机号',
    `contact_email`        varchar(254)          DEFAULT NULL COMMENT '联系人邮箱',
    `status`               tinyint      NOT NULL DEFAULT 0 COMMENT '租户状态：0-正常；1-停用；2-过期',
    `package_id`           bigint       NOT NULL COMMENT '租户套餐ID',
    `expire_time`          datetime     NOT NULL COMMENT '过期时间',
    `max_account_count`    int          NOT NULL DEFAULT 0 COMMENT '最大账号数量',
    `is_deleted`           tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `version`              int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`            bigint                DEFAULT NULL COMMENT '创建人ID',
    `create_time`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`            bigint                DEFAULT NULL COMMENT '更新人ID',
    `update_time`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `active_tenant_code`   varchar(64)  GENERATED ALWAYS AS (IF(`is_deleted` = 0, `tenant_code`, NULL)) STORED,
    `active_tenant_name`   varchar(128) GENERATED ALWAYS AS (IF(`is_deleted` = 0, `tenant_name`, NULL)) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_saas_tenant_active_tenant_code` (`active_tenant_code`),
    UNIQUE KEY `uk_saas_tenant_active_tenant_name` (`active_tenant_name`),
    INDEX `idx_saas_tenant_package`      (`package_id`),
    INDEX `idx_saas_tenant_status_expire` (`status`, `expire_time`),
    INDEX `idx_saas_tenant_deleted`       (`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='SaaS-租户表';

DROP TABLE IF EXISTS `saas_tenant_domain`;
CREATE TABLE `saas_tenant_domain`
(
    `id`                 bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`          bigint       NOT NULL COMMENT '租户ID',
    `domain`             varchar(255) NOT NULL COMMENT '域名',
    `is_primary`         tinyint      NOT NULL DEFAULT 0 COMMENT '是否主域名',
    `verification_token` varchar(128)          DEFAULT NULL COMMENT '域名验证令牌',
    `verified_time`      datetime              DEFAULT NULL COMMENT '验证通过时间',
    `is_deleted`         tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `version`            int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`          bigint                DEFAULT NULL COMMENT '创建人ID',
    `create_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`          bigint                DEFAULT NULL COMMENT '更新人ID',
    `update_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `active_domain`      varchar(255) GENERATED ALWAYS AS (IF(`is_deleted` = 0, `domain`, NULL)) STORED,
    `primary_marker`     tinyint      GENERATED ALWAYS AS (IF(`is_deleted` = 0 AND `is_primary` = 1, 1, NULL)) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_saas_tenant_domain_active_domain`  (`active_domain`),
    UNIQUE KEY `uk_saas_tenant_domain_tenant_primary` (`tenant_id`, `primary_marker`),
    INDEX `idx_saas_tenant_domain_tenant_deleted`     (`tenant_id`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='SaaS-租户域名表';

DROP TABLE IF EXISTS `saas_package`;
CREATE TABLE `saas_package`
(
    `id`                  bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `package_name`        varchar(128) NOT NULL COMMENT '套餐名称：全局唯一',
    `status`              tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0-正常；1-停用',
    `description`         varchar(500)          DEFAULT NULL COMMENT '描述',
    `is_deleted`          tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `version`             int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`           bigint                DEFAULT NULL COMMENT '创建人ID',
    `create_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`           bigint                DEFAULT NULL COMMENT '更新人ID',
    `update_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `active_package_name` varchar(128) GENERATED ALWAYS AS (IF(`is_deleted` = 0, `package_name`, NULL)) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_saas_package_active_package_name` (`active_package_name`),
    INDEX `idx_saas_package_status_deleted` (`status`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='SaaS-租户套餐表';

DROP TABLE IF EXISTS `saas_package_menu`;
CREATE TABLE `saas_package_menu`
(
    `id`          bigint   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `package_id`  bigint   NOT NULL COMMENT '套餐ID',
    `menu_id`     bigint   NOT NULL COMMENT '菜单ID',
    `create_by`   bigint            DEFAULT NULL COMMENT '创建人ID',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_saas_package_menu_package_menu` (`package_id`, `menu_id`),
    INDEX `idx_saas_package_menu_menu` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='SaaS-租户套餐与菜单关联表';

DROP TABLE IF EXISTS `saas_tenant_package_history`;
CREATE TABLE `saas_tenant_package_history`
(
    `id`             bigint   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`      bigint   NOT NULL COMMENT '租户ID',
    `old_package_id` bigint            DEFAULT NULL COMMENT '原套餐ID',
    `new_package_id` bigint   NOT NULL COMMENT '新套餐ID',
    `change_reason`  varchar(500)       DEFAULT NULL COMMENT '变更原因',
    `effective_time` datetime NOT NULL COMMENT '生效时间',
    `create_by`      bigint            DEFAULT NULL COMMENT '创建人ID',
    `create_time`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_saas_tph_tenant_time` (`tenant_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='SaaS-租户套餐变更历史表';

-- =====================================================================
-- Msg 消息模块
-- =====================================================================

DROP TABLE IF EXISTS `msg_sms_template`;
CREATE TABLE `msg_sms_template`
(
    `id`                   bigint        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `template_code`        varchar(64)   NOT NULL COMMENT '模板编码：全局唯一',
    `template_name`        varchar(128)  NOT NULL COMMENT '模板名称',
    `template_type`        tinyint       NOT NULL COMMENT '模板类型：1-验证码；2-通知；3-营销',
    `channel_id`           bigint        NOT NULL COMMENT '短信渠道ID',
    `channel_code`         varchar(64)   NOT NULL COMMENT '短信渠道编码',
    `provider_template_id` varchar(64)   NOT NULL COMMENT '第三方模板编号',
    `content`              varchar(2048) NOT NULL COMMENT '模板内容',
    `params`               json                   DEFAULT NULL COMMENT '模板参数数组',
    `status`               tinyint       NOT NULL DEFAULT 0 COMMENT '状态：0-启用；1-禁用',
    `description`          varchar(255)           DEFAULT NULL COMMENT '描述',
    `is_deleted`           tinyint       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `version`              int           NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`            bigint                 DEFAULT NULL COMMENT '创建人ID',
    `create_time`          datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`            bigint                 DEFAULT NULL COMMENT '更新人ID',
    `update_time`          datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `active_template_code` varchar(64)   GENERATED ALWAYS AS (IF(`is_deleted` = 0, `template_code`, NULL)) STORED,
    `active_template_name` varchar(128)  GENERATED ALWAYS AS (IF(`is_deleted` = 0, `template_name`, NULL)) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_msg_sms_template_active_code` (`active_template_code`),
    UNIQUE KEY `uk_msg_sms_template_active_name` (`active_template_name`),
    INDEX `idx_msg_sms_template_channel`             (`channel_id`),
    INDEX `idx_msg_sms_template_type_status_deleted` (`template_type`, `status`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='消息-短信模板表';

DROP TABLE IF EXISTS `msg_sms_channel`;
CREATE TABLE `msg_sms_channel`
(
    `id`                      bigint          NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `channel_code`            varchar(64)     NOT NULL COMMENT '渠道编码：全局唯一',
    `channel_name`            varchar(128)    NOT NULL COMMENT '渠道名称',
    `signature`               varchar(64)     NOT NULL COMMENT '短信签名',
    `encrypted_access_key`    varbinary(2048) NOT NULL COMMENT '加密后的 AccessKey',
    `encrypted_access_secret` varbinary(2048) NOT NULL COMMENT '加密后的 AccessSecret',
    `callback_url`            varchar(1024)            DEFAULT NULL COMMENT '回调 URL',
    `status`                  tinyint         NOT NULL DEFAULT 0 COMMENT '状态：0-启用；1-禁用',
    `description`             varchar(255)             DEFAULT NULL COMMENT '描述',
    `is_deleted`              tinyint         NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `version`                 int             NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`               bigint                   DEFAULT NULL COMMENT '创建人ID',
    `create_time`             datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`               bigint                   DEFAULT NULL COMMENT '更新人ID',
    `update_time`             datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `active_channel_code`     varchar(64)     GENERATED ALWAYS AS (IF(`is_deleted` = 0, `channel_code`, NULL)) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_msg_sms_channel_active_code` (`active_channel_code`),
    INDEX `idx_msg_sms_channel_status_deleted` (`status`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='消息-短信渠道配置表';

DROP TABLE IF EXISTS `msg_sms_send_log`;
CREATE TABLE `msg_sms_send_log`
(
    `id`                       bigint        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `channel_id`               bigint        NOT NULL COMMENT '短信渠道ID',
    `channel_code`             varchar(64)   NOT NULL COMMENT '渠道编码',
    `template_id`              bigint        NOT NULL COMMENT '模板ID',
    `template_code`            varchar(64)   NOT NULL COMMENT '模板编码（快照）',
    `template_type`            tinyint       NOT NULL COMMENT '模板类型',
    `template_content`         varchar(2048) NOT NULL COMMENT '短信内容（快照）',
    `template_params`          json                   DEFAULT NULL COMMENT '模板参数（快照）',
    `provider_template_id`     varchar(64)   NOT NULL COMMENT '第三方模板编号',
    `phone_number`             varchar(20)   NOT NULL COMMENT '手机号',
    `user_id`                  bigint                 DEFAULT NULL COMMENT '用户ID',
    `user_type`                varchar(20)            DEFAULT NULL COMMENT '用户类型',
    `send_status`              tinyint       NOT NULL DEFAULT 0 COMMENT '发送状态',
    `send_time`                datetime               DEFAULT NULL COMMENT '发送时间',
    `provider_send_code`       varchar(64)            DEFAULT NULL COMMENT '服务商结果编码',
    `provider_send_message`    varchar(512)           DEFAULT NULL COMMENT '服务商结果消息',
    `provider_request_id`      varchar(128)           DEFAULT NULL COMMENT '服务商请求ID',
    `provider_serial_number`   varchar(128)           DEFAULT NULL COMMENT '服务商流水号',
    `receive_status`           tinyint       NOT NULL DEFAULT 0 COMMENT '接收状态',
    `receive_time`             datetime               DEFAULT NULL COMMENT '接收时间',
    `provider_receive_code`    varchar(64)            DEFAULT NULL COMMENT '接收结果编码',
    `provider_receive_message` varchar(512)           DEFAULT NULL COMMENT '接收结果说明',
    `retry_count`              int           NOT NULL DEFAULT 0 COMMENT '重试次数',
    `next_retry_time`          datetime               DEFAULT NULL COMMENT '下次重试时间',
    `idempotent_key`           varchar(128)           DEFAULT NULL COMMENT '幂等键',
    `tenant_id`                bigint        NOT NULL DEFAULT 0 COMMENT '租户ID',
    `create_time`              datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_msg_sms_send_log_idempotent`      (`tenant_id`, `idempotent_key`),
    INDEX `idx_msg_sms_send_log_phone_time`          (`phone_number`, `create_time`),
    INDEX `idx_msg_sms_send_log_tenant_user_time`    (`tenant_id`, `user_id`, `create_time`),
    INDEX `idx_msg_sms_send_log_template_time`       (`template_code`, `create_time`),
    INDEX `idx_msg_sms_send_log_status_time`         (`send_status`, `create_time`),
    INDEX `idx_msg_sms_send_log_time`                (`create_time`),
    INDEX `idx_msg_sms_send_log_next_retry`          (`next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='消息-短信发送日志表';

DROP TABLE IF EXISTS `msg_sms_code`;
CREATE TABLE `msg_sms_code`
(
    `id`                 bigint        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `phone_number`       varchar(20)   NOT NULL COMMENT '手机号',
    `code_hash`          varchar(128)  NOT NULL COMMENT '验证码哈希',
    `scene`              tinyint       NOT NULL COMMENT '发送场景',
    `tenant_id`          bigint        NOT NULL DEFAULT 0 COMMENT '租户ID',
    `request_ip_address` varchar(45)   NOT NULL COMMENT '请求 IP',
    `user_agent`         varchar(1024)          DEFAULT NULL COMMENT '客户端 User-Agent',
    `is_used`            tinyint       NOT NULL DEFAULT 0 COMMENT '是否已使用',
    `use_time`           datetime               DEFAULT NULL COMMENT '使用时间',
    `use_ip_address`     varchar(45)            DEFAULT NULL COMMENT '使用 IP',
    `daily_sequence`     smallint      NOT NULL DEFAULT 1 COMMENT '当日发送第几条',
    `expire_time`        datetime      NOT NULL COMMENT '过期时间',
    `create_time`        datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_msg_sms_code_phone_scene_time` (`phone_number`, `scene`, `create_time`),
    INDEX `idx_msg_sms_code_expire`           (`expire_time`),
    INDEX `idx_msg_sms_code_tenant_time`      (`tenant_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='消息-短信验证码表';

DROP TABLE IF EXISTS `msg_inbox_template`;
CREATE TABLE `msg_inbox_template`
(
    `id`                   bigint        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `template_code`        varchar(64)   NOT NULL COMMENT '模板编码：全局唯一',
    `template_name`        varchar(128)  NOT NULL COMMENT '模板名称',
    `template_type`        tinyint       NOT NULL COMMENT '模板类型',
    `sender_name`          varchar(64)   NOT NULL COMMENT '发送人名称',
    `content`              varchar(2048) NOT NULL COMMENT '模板内容',
    `params`               json                   DEFAULT NULL COMMENT '模板参数数组',
    `status`               tinyint       NOT NULL DEFAULT 0 COMMENT '状态',
    `description`          varchar(255)           DEFAULT NULL COMMENT '描述',
    `is_deleted`           tinyint       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `version`              int           NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`            bigint                 DEFAULT NULL COMMENT '创建人ID',
    `create_time`          datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`            bigint                 DEFAULT NULL COMMENT '更新人ID',
    `update_time`          datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `active_template_code` varchar(64)   GENERATED ALWAYS AS (IF(`is_deleted` = 0, `template_code`, NULL)) STORED,
    `active_template_name` varchar(128)  GENERATED ALWAYS AS (IF(`is_deleted` = 0, `template_name`, NULL)) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_msg_inbox_template_active_code` (`active_template_code`),
    UNIQUE KEY `uk_msg_inbox_template_active_name` (`active_template_name`),
    INDEX `idx_msg_inbox_template_type_status_deleted` (`template_type`, `status`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='消息-站内信模板表';

DROP TABLE IF EXISTS `msg_inbox_message`;
CREATE TABLE `msg_inbox_message`
(
    `id`                   bigint        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`              bigint        NOT NULL COMMENT '接收用户ID',
    `user_type`            varchar(20)   NOT NULL COMMENT '用户类型',
    `template_id`          bigint        NOT NULL COMMENT '模板ID',
    `template_code`        varchar(64)   NOT NULL COMMENT '模板编码（快照）',
    `template_type`        tinyint       NOT NULL COMMENT '模板类型',
    `template_sender_name` varchar(64)   NOT NULL COMMENT '发送人名称（快照）',
    `template_content`     varchar(2048) NOT NULL COMMENT '渲染后的消息内容（快照）',
    `template_params`      json                   DEFAULT NULL COMMENT '模板参数（快照）',
    `is_read`              tinyint       NOT NULL DEFAULT 0 COMMENT '是否已读',
    `read_time`            datetime               DEFAULT NULL COMMENT '阅读时间',
    `tenant_id`            bigint        NOT NULL DEFAULT 0 COMMENT '租户ID',
    `is_deleted`           tinyint       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `version`              int           NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`            bigint                 DEFAULT NULL COMMENT '创建人ID',
    `create_time`          datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`            bigint                 DEFAULT NULL COMMENT '更新人ID',
    `update_time`          datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_msg_inbox_message_user_unread`      (`user_id`, `user_type`, `is_read`, `create_time`),
    INDEX `idx_msg_inbox_message_tenant_user_time` (`tenant_id`, `user_id`, `user_type`, `create_time`),
    INDEX `idx_msg_inbox_message_deleted`          (`tenant_id`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='消息-站内信消息表';

DROP TABLE IF EXISTS `msg_announcement`;
CREATE TABLE `msg_announcement`
(
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `title`       varchar(255) NOT NULL COMMENT '公告标题',
    `content`     text         NOT NULL COMMENT '公告内容',
    `notice_type` tinyint      NOT NULL COMMENT '公告类型',
    `status`      tinyint      NOT NULL DEFAULT 0 COMMENT '公告状态',
    `tenant_id`   bigint       NOT NULL DEFAULT 0 COMMENT '租户ID',
    `is_deleted`  tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `version`     int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`   bigint                DEFAULT NULL COMMENT '创建人ID',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   bigint                DEFAULT NULL COMMENT '更新人ID',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_msg_announcement_tenant_type`        (`tenant_id`, `notice_type`),
    INDEX `idx_msg_announcement_tenant_status_time` (`tenant_id`, `status`, `create_time`),
    INDEX `idx_msg_announcement_tenant_deleted`     (`tenant_id`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='消息-通知公告表';

DROP TABLE IF EXISTS `msg_mail_template`;
CREATE TABLE `msg_mail_template`
(
    `id`                   bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `template_code`        varchar(64)  NOT NULL COMMENT '模板编码：全局唯一',
    `template_name`        varchar(128) NOT NULL COMMENT '模板名称',
    `mail_account_id`      bigint       NOT NULL COMMENT '发送邮箱账号ID',
    `sender_name`          varchar(64)           DEFAULT NULL COMMENT '发送人名称',
    `subject`              varchar(255) NOT NULL COMMENT '邮件主题',
    `content`              mediumtext   NOT NULL COMMENT '邮件内容',
    `params`               json                  DEFAULT NULL COMMENT '模板参数数组',
    `status`               tinyint      NOT NULL DEFAULT 0 COMMENT '状态',
    `description`          varchar(255)          DEFAULT NULL COMMENT '描述',
    `is_deleted`           tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `version`              int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`            bigint                DEFAULT NULL COMMENT '创建人ID',
    `create_time`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`            bigint                DEFAULT NULL COMMENT '更新人ID',
    `update_time`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `active_template_code` varchar(64)  GENERATED ALWAYS AS (IF(`is_deleted` = 0, `template_code`, NULL)) STORED,
    `active_template_name` varchar(128) GENERATED ALWAYS AS (IF(`is_deleted` = 0, `template_name`, NULL)) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_msg_mail_template_active_code` (`active_template_code`),
    UNIQUE KEY `uk_msg_mail_template_active_name` (`active_template_name`),
    INDEX `idx_msg_mail_template_account`        (`mail_account_id`),
    INDEX `idx_msg_mail_template_status_deleted` (`status`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='消息-邮件模板表';

DROP TABLE IF EXISTS `msg_mail_account`;
CREATE TABLE `msg_mail_account`
(
    `id`                      bigint          NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `account_name`            varchar(64)     NOT NULL COMMENT '账号名称：全局唯一',
    `from_email`              varchar(254)    NOT NULL COMMENT '发件人邮箱地址',
    `sender_name`             varchar(64)              DEFAULT NULL COMMENT '发件人显示名称',
    `smtp_host`               varchar(255)    NOT NULL COMMENT 'SMTP 服务器域名',
    `smtp_port`               int             NOT NULL COMMENT 'SMTP 服务器端口',
    `encrypted_smtp_username` varbinary(4096) NOT NULL COMMENT '加密后的 SMTP 用户名',
    `encrypted_smtp_password` varbinary(2048) NOT NULL COMMENT '加密后的 SMTP 密码',
    `is_ssl_enabled`          tinyint         NOT NULL DEFAULT 0 COMMENT '是否启用 SSL',
    `is_starttls_enabled`     tinyint         NOT NULL DEFAULT 0 COMMENT '是否启用 STARTTLS',
    `status`                  tinyint         NOT NULL DEFAULT 0 COMMENT '状态',
    `description`             varchar(255)             DEFAULT NULL COMMENT '描述',
    `is_deleted`              tinyint         NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `version`                 int             NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`               bigint                   DEFAULT NULL COMMENT '创建人ID',
    `create_time`             datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`               bigint                   DEFAULT NULL COMMENT '更新人ID',
    `update_time`             datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `active_account_name`     varchar(64)     GENERATED ALWAYS AS (IF(`is_deleted` = 0, `account_name`, NULL)) STORED,
    `active_from_email`       varchar(254)    GENERATED ALWAYS AS (IF(`is_deleted` = 0, `from_email`, NULL)) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_msg_mail_account_active_name`       (`active_account_name`),
    UNIQUE KEY `uk_msg_mail_account_active_from_email` (`active_from_email`),
    INDEX `idx_msg_mail_account_status_deleted`        (`status`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='消息-邮箱账号配置表';

DROP TABLE IF EXISTS `msg_mail_send_log`;
CREATE TABLE `msg_mail_send_log`
(
    `id`                   bigint        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`              bigint                 DEFAULT NULL COMMENT '接收用户ID',
    `user_type`            varchar(20)            DEFAULT NULL COMMENT '用户类型',
    `to_recipients`        varchar(2048) NOT NULL COMMENT '收件人邮箱地址',
    `cc_recipients`        varchar(2048)          DEFAULT NULL COMMENT '抄送邮箱地址',
    `bcc_recipients`       varchar(2048)          DEFAULT NULL COMMENT '密送邮箱地址',
    `mail_account_id`      bigint        NOT NULL COMMENT '邮箱账号ID',
    `from_email`           varchar(254)  NOT NULL COMMENT '发件人邮箱地址',
    `template_id`          bigint        NOT NULL COMMENT '模板ID',
    `template_code`        varchar(64)   NOT NULL COMMENT '模板编码（快照）',
    `template_sender_name` varchar(64)            DEFAULT NULL COMMENT '发送人名称（快照）',
    `template_subject`     varchar(255)  NOT NULL COMMENT '邮件主题（快照）',
    `template_content`     mediumtext    NOT NULL COMMENT '邮件内容（快照）',
    `template_params`      json                   DEFAULT NULL COMMENT '模板参数（快照）',
    `send_status`          tinyint       NOT NULL DEFAULT 0 COMMENT '发送状态',
    `send_time`            datetime               DEFAULT NULL COMMENT '发送时间',
    `provider_message_id`  varchar(255)           DEFAULT NULL COMMENT '服务商消息 ID',
    `error_message`        text                   DEFAULT NULL COMMENT '发送失败异常信息',
    `retry_count`          int           NOT NULL DEFAULT 0 COMMENT '重试次数',
    `next_retry_time`      datetime               DEFAULT NULL COMMENT '下次重试时间',
    `idempotent_key`       varchar(128)           DEFAULT NULL COMMENT '幂等键',
    `tenant_id`            bigint        NOT NULL DEFAULT 0 COMMENT '租户ID',
    `create_time`          datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_msg_mail_send_log_idempotent`     (`tenant_id`, `idempotent_key`),
    INDEX `idx_msg_mail_send_log_user_time`          (`user_id`, `create_time`),
    INDEX `idx_msg_mail_send_log_to_time`            (`to_recipients`(255), `create_time`),
    INDEX `idx_msg_mail_send_log_tenant_status_time` (`tenant_id`, `send_status`, `create_time`),
    INDEX `idx_msg_mail_send_log_time`               (`create_time`),
    INDEX `idx_msg_mail_send_log_next_retry`         (`next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='消息-邮件发送日志表';

-- =====================================================================
-- Audit 审计模块
-- =====================================================================

DROP TABLE IF EXISTS `audit_operation_log`;
CREATE TABLE `audit_operation_log`
(
    `id`               bigint        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `trace_id`         varchar(64)   NOT NULL COMMENT '链路追踪ID',
    `user_id`          bigint        NOT NULL DEFAULT 0 COMMENT '操作用户ID',
    `user_type`        varchar(20)   NOT NULL DEFAULT 'EXTERNAL' COMMENT '用户类型',
    `ip_address`       varchar(45)            DEFAULT NULL COMMENT '操作来源 IP',
    `user_agent`       varchar(1024)          DEFAULT NULL COMMENT '客户端 User-Agent',
    `http_method`      varchar(16)            DEFAULT NULL COMMENT 'HTTP 方法',
    `request_url`      varchar(2048)          DEFAULT NULL COMMENT '请求 URL',
    `operation_module` varchar(64)   NOT NULL COMMENT '操作模块',
    `operation_name`   varchar(64)   NOT NULL COMMENT '操作名称',
    `business_id`      bigint        NOT NULL DEFAULT 0 COMMENT '业务数据ID',
    `action`           text          NOT NULL COMMENT '操作内容描述',
    `is_success`       tinyint       NOT NULL DEFAULT 1 COMMENT '操作结果',
    `extra_data`       mediumtext             DEFAULT NULL COMMENT '扩展数据',
    `prev_hash`        varchar(64)            DEFAULT NULL COMMENT '前一条记录哈希',
    `record_hash`      varchar(64)            DEFAULT NULL COMMENT '本条记录哈希',
    `tenant_id`        bigint        NOT NULL DEFAULT 0 COMMENT '租户ID',
    `create_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_audit_operation_log_tenant_user_time` (`tenant_id`, `user_id`, `create_time`),
    INDEX `idx_audit_operation_log_module_time`      (`operation_module`, `create_time`),
    INDEX `idx_audit_operation_log_time`             (`create_time`),
    INDEX `idx_audit_operation_log_trace`            (`trace_id`),
    INDEX `idx_audit_operation_log_record_hash`      (`record_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='审计-操作日志表';

DROP TABLE IF EXISTS `audit_login_log`;
CREATE TABLE `audit_login_log`
(
    `id`           bigint        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `trace_id`     varchar(64)   NOT NULL COMMENT '链路追踪ID',
    `user_id`      bigint        NOT NULL DEFAULT 0 COMMENT '用户ID',
    `user_type`    varchar(20)   NOT NULL DEFAULT 'EXTERNAL' COMMENT '用户类型',
    `username`     varchar(255)  NOT NULL COMMENT '登录标识（快照，可能是用户名/手机号/邮箱）',
    `login_type`   varchar(32)   NOT NULL COMMENT '登录类型：PASSWORD/SMS/WECHAT/GOOGLE/GITHUB/OIDC',
    `login_result` tinyint       NOT NULL COMMENT '登录结果',
    `ip_address`   varchar(45)   NOT NULL COMMENT '登录来源 IP',
    `user_agent`   varchar(1024) NOT NULL COMMENT '客户端 User-Agent',
    `tenant_id`    bigint        NOT NULL DEFAULT 0 COMMENT '租户ID',
    `create_time`  datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_audit_login_log_username_time`    (`username`, `create_time`),
    INDEX `idx_audit_login_log_tenant_user_time` (`tenant_id`, `user_id`, `user_type`, `create_time`),
    INDEX `idx_audit_login_log_result_time`      (`login_result`, `create_time`),
    INDEX `idx_audit_login_log_time`             (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='审计-登录日志表';

-- =====================================================================
-- Dict 字典模块
-- =====================================================================

DROP TABLE IF EXISTS `dict_type`;
CREATE TABLE `dict_type`
(
    `id`                    bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `dict_type_code`        varchar(100) NOT NULL COMMENT '字典类型编码：全局唯一',
    `dict_type_name`        varchar(128) NOT NULL COMMENT '字典类型名称',
    `status`                tinyint      NOT NULL DEFAULT 0 COMMENT '状态',
    `description`           varchar(500)          DEFAULT NULL COMMENT '描述',
    `is_deleted`            tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `version`               int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`             bigint                DEFAULT NULL COMMENT '创建人ID',
    `create_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`             bigint                DEFAULT NULL COMMENT '更新人ID',
    `update_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `active_dict_type_code` varchar(100) GENERATED ALWAYS AS (IF(`is_deleted` = 0, `dict_type_code`, NULL)) STORED,
    `active_dict_type_name` varchar(128) GENERATED ALWAYS AS (IF(`is_deleted` = 0, `dict_type_name`, NULL)) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dict_type_active_code` (`active_dict_type_code`),
    UNIQUE KEY `uk_dict_type_active_name` (`active_dict_type_name`),
    INDEX `idx_dict_type_status_deleted`  (`status`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='字典-字典类型表';

DROP TABLE IF EXISTS `dict_item`;
CREATE TABLE `dict_item`
(
    `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `dict_type_code`    varchar(100) NOT NULL COMMENT '字典类型编码',
    `dict_label`        varchar(128) NOT NULL COMMENT '字典标签',
    `dict_value`        varchar(100) NOT NULL COMMENT '字典键值',
    `sort_order`        int          NOT NULL DEFAULT 0 COMMENT '显示顺序',
    `status`            tinyint      NOT NULL DEFAULT 0 COMMENT '状态',
    `color_type`        varchar(32)           DEFAULT NULL COMMENT '颜色类型',
    `css_class`         varchar(100)          DEFAULT NULL COMMENT 'CSS样式类名',
    `description`       varchar(500)          DEFAULT NULL COMMENT '描述',
    `is_deleted`        tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `version`           int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`         bigint                DEFAULT NULL COMMENT '创建人ID',
    `create_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`         bigint                DEFAULT NULL COMMENT '更新人ID',
    `update_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `active_dict_value` varchar(100) GENERATED ALWAYS AS (IF(`is_deleted` = 0, `dict_value`, NULL)) STORED,
    `active_dict_label` varchar(128) GENERATED ALWAYS AS (IF(`is_deleted` = 0, `dict_label`, NULL)) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dict_item_type_value` (`dict_type_code`, `active_dict_value`),
    UNIQUE KEY `uk_dict_item_type_label` (`dict_type_code`, `active_dict_label`),
    INDEX `idx_dict_item_type_sort`      (`dict_type_code`, `sort_order`),
    INDEX `idx_dict_item_status_deleted` (`status`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='字典-字典数据表';

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
    `logo_url`                  varchar(1024)          DEFAULT NULL COMMENT '应用图标 URL',
    `description`               varchar(512)           DEFAULT NULL COMMENT '应用描述',
    `status`                    tinyint       NOT NULL DEFAULT 0 COMMENT '状态',
    `access_token_ttl_seconds`  int           NOT NULL COMMENT '访问令牌有效期（秒）',
    `refresh_token_ttl_seconds` int           NOT NULL COMMENT '刷新令牌有效期（秒）',
    `redirect_uris`             json          NOT NULL COMMENT '可重定向的 URI 数组',
    `authorized_grant_types`    json          NOT NULL COMMENT '授权类型数组',
    `scopes`                    json                   DEFAULT NULL COMMENT '授权范围数组',
    `auto_approve_scopes`       json                   DEFAULT NULL COMMENT '自动通过的授权范围数组',
    `authorities`               json                   DEFAULT NULL COMMENT '权限数组',
    `resource_ids`              json                   DEFAULT NULL COMMENT '资源ID数组',
    `additional_information`    json                   DEFAULT NULL COMMENT '附加信息',
    `tenant_id`                 bigint        NOT NULL DEFAULT 0 COMMENT '租户ID',
    `is_deleted`                tinyint       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `version`                   int           NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`                 bigint                 DEFAULT NULL COMMENT '创建人ID',
    `create_time`               datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`                 bigint                 DEFAULT NULL COMMENT '更新人ID',
    `update_time`               datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `active_client_id`          varchar(128)  GENERATED ALWAYS AS (IF(`is_deleted` = 0, `client_id`, NULL)) STORED,
    `active_client_name`        varchar(128)  GENERATED ALWAYS AS (IF(`is_deleted` = 0, `client_name`, NULL)) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_oauth_client_tenant_active_client_id`   (`tenant_id`, `active_client_id`),
    UNIQUE KEY `uk_oauth_client_tenant_active_client_name` (`tenant_id`, `active_client_name`),
    INDEX `idx_oauth_client_tenant_deleted_status`         (`tenant_id`, `is_deleted`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='OAuth-客户端配置表';

DROP TABLE IF EXISTS `oauth_code`;
CREATE TABLE `oauth_code`
(
    `id`                      bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `authorization_code_hash` varchar(128) NOT NULL COMMENT '授权码哈希',
    `client_id`               varchar(128) NOT NULL COMMENT '客户端编号',
    `user_id`                 bigint       NOT NULL COMMENT '用户ID',
    `user_type`               varchar(20)  NOT NULL COMMENT '用户类型',
    `scopes`                  json                  DEFAULT NULL COMMENT '授权范围数组',
    `redirect_uri`            varchar(1024)         DEFAULT NULL COMMENT '重定向 URI',
    `state`                   varchar(255)          DEFAULT NULL COMMENT '状态参数',
    `is_used`                 tinyint      NOT NULL DEFAULT 0 COMMENT '是否已使用',
    `use_time`                datetime              DEFAULT NULL COMMENT '使用时间',
    `expire_time`             datetime     NOT NULL COMMENT '过期时间',
    `tenant_id`               bigint       NOT NULL DEFAULT 0 COMMENT '租户ID',
    `create_time`             datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_oauth_code_hash`        (`authorization_code_hash`),
    INDEX `idx_oauth_code_expire`          (`expire_time`),
    INDEX `idx_oauth_code_tenant_user`     (`tenant_id`, `user_id`, `user_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='OAuth-授权码表';

DROP TABLE IF EXISTS `oauth_refresh_token`;
CREATE TABLE `oauth_refresh_token`
(
    `id`                 bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `refresh_token_hash` varchar(128) NOT NULL COMMENT '刷新令牌哈希',
    `client_id`          varchar(128) NOT NULL COMMENT '客户端编号',
    `user_id`            bigint       NOT NULL COMMENT '用户ID',
    `user_type`          varchar(20)  NOT NULL COMMENT '用户类型',
    `scopes`             json                  DEFAULT NULL COMMENT '授权范围数组',
    `is_revoked`         tinyint      NOT NULL DEFAULT 0 COMMENT '是否已撤销',
    `revoke_time`        datetime              DEFAULT NULL COMMENT '撤销时间',
    `expire_time`        datetime     NOT NULL COMMENT '过期时间',
    `tenant_id`          bigint       NOT NULL DEFAULT 0 COMMENT '租户ID',
    `create_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_oauth_refresh_token_hash`    (`refresh_token_hash`),
    INDEX `idx_oauth_refresh_token_tenant_user` (`tenant_id`, `user_id`, `user_type`),
    INDEX `idx_oauth_refresh_token_expire`      (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='OAuth-刷新令牌表';

DROP TABLE IF EXISTS `oauth_access_token`;
CREATE TABLE `oauth_access_token`
(
    `id`                 bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `access_token_hash`  varchar(128) NOT NULL COMMENT '访问令牌哈希',
    `refresh_token_hash` varchar(128)          DEFAULT NULL COMMENT '刷新令牌哈希',
    `client_id`          varchar(128) NOT NULL COMMENT '客户端编号',
    `user_id`            bigint       NOT NULL COMMENT '用户ID',
    `user_type`          varchar(20)  NOT NULL COMMENT '用户类型',
    `user_info`          json                  DEFAULT NULL COMMENT '用户信息快照',
    `scopes`             json                  DEFAULT NULL COMMENT '授权范围数组',
    `is_revoked`         tinyint      NOT NULL DEFAULT 0 COMMENT '是否已撤销',
    `revoke_time`        datetime              DEFAULT NULL COMMENT '撤销时间',
    `expire_time`        datetime     NOT NULL COMMENT '过期时间',
    `tenant_id`          bigint       NOT NULL DEFAULT 0 COMMENT '租户ID',
    `create_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_oauth_access_token_hash`        (`access_token_hash`),
    INDEX `idx_oauth_access_token_refresh_hash`    (`refresh_token_hash`),
    INDEX `idx_oauth_access_token_tenant_user`     (`tenant_id`, `user_id`, `user_type`),
    INDEX `idx_oauth_access_token_client`          (`client_id`),
    INDEX `idx_oauth_access_token_expire`          (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='OAuth-访问令牌表';

DROP TABLE IF EXISTS `oauth_consent`;
CREATE TABLE `oauth_consent`
(
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `client_id`   varchar(128) NOT NULL COMMENT '客户端编号',
    `user_id`     bigint       NOT NULL COMMENT '用户ID',
    `user_type`   varchar(20)  NOT NULL COMMENT '用户类型',
    `scopes`      json         NOT NULL COMMENT '已批准的授权范围数组',
    `is_approved` tinyint      NOT NULL DEFAULT 0 COMMENT '是否已批准',
    `expire_time` datetime     NOT NULL COMMENT '过期时间',
    `tenant_id`   bigint       NOT NULL DEFAULT 0 COMMENT '租户ID',
    `version`     int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`   bigint                DEFAULT NULL COMMENT '创建人ID',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   bigint                DEFAULT NULL COMMENT '更新人ID',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_oauth_consent_tenant_client_user` (`tenant_id`, `client_id`, `user_id`, `user_type`),
    INDEX `idx_oauth_consent_user`   (`user_id`, `user_type`),
    INDEX `idx_oauth_consent_expire` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='OAuth-授权同意表';

-- =====================================================================
-- End of Realm DB Schema v3.0.0 · CN Edition · OneID Edition
-- =====================================================================
SET FOREIGN_KEY_CHECKS = 1;
