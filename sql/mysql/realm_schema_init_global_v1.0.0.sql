-- =====================================================================
-- Realm DB Schema v1.0.0 · Global Edition (MySQL 5.7)
-- =====================================================================

-- 任务：统一优化数据库表名、字段名、表注释、字段注释。
-- 依据：国际标准，以及全球领先企业的通用规范/最佳实践。
-- 目标：使命名和注释统一、规范、一致、清晰。
-- 要求：不改变原有业务语义；输出优化后的表名、字段名、表注释和字段注释。


-- 国际主流、可扩展、可长期演进的数据库设计
-- 全球顶级企业在处理复杂系统时，都倾向于按业务域（Domain）来隔离数据
-- 全球业界公认的缩写有哪些。


-- 国际标准推荐表名，国际主流做法是按业务域分

-- 国际标准字段名，统一后缀/前缀

-- 国际标准字段注释模板，统一

-- 国际标准的核心是严格区分 NULL（未知）和 ''（已知的空字符串）。全球领先企业的趋势是优先使用 NULL 来表示缺失数据，因为它语义更精确，且能避免与具有业务含义的空字符串混淆。

-- 全球顶级企业标准：审计字段通常
-- 国际标准常用 created_at, updated_at, created_by, updated_by, deleted_at, version。
-- 公共字段标准 DDL

    `version`int NOT NULL DEFAULT 1 COMMENT '乐观锁版本号（用于并发控制）',
    `deleted_at` datetime(3) NULL DEFAULT NULL COMMENT '删除时间（UTC），NULL表示未删除',
    `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
    `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
    `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）'

-- MySQL 唯一索引中，deleted_at 为 NULL 时，NULL != NULL，因此未删除记录可以重复插入相同 code + tenant_id。这会让唯一约束形同虚设。推荐用生成列。

-- 如果国内项目，建议统一为 create_time, update_time, create_by, update_by, deleted, version, tenant_id。
-- is_deleted vs deleted。  默认推荐：is_deleted。  SQL 优先：is_deleted。  更好的方案：软删除用 deleted_at。

-- 符合国内 MyBatis-Plus 生态推荐的公共字段 DDL

    `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除：0-否；1-是',
    `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `version` int NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认，非 0 时关联 saas_tenant.id'


-- 分析还有什么需要优化。需要给出建议。需要全面、专业。可以结构化：总体评价、命名/域、公共字段/审计、软删除、多租户、索引/唯一键、数据类型、时间/时区、金额/数量、状态枚举、注释、外键、DDL 细节、表分类（业务主表/关联表/日志表/临时表/大字段表）、安全加密、JSON、生成列唯一约束、MySQL 版本兼容、排序/索引命名、冗余字段、字符集/排序规则、AUTO_INCREMENT 类型、timestamp vs datetime、varchar 长度、布尔类型、幂等、分区/归档、合规等。


-- 建议的最终规范模板
-- 表名：<域>_<实体>，单数，小写蛇形
-- 字段：id, xxx_id, xxx_code, xxx_name, xxx_type, xxx_status, is_xxx,
--       xxx_at, created_by, created_at, updated_by, updated_at,
--       deleted_at, version, tenant_id
-- 唯一约束：active_xxx 生成列 + 唯一键
-- 普通索引：idx_<table>_<columns>
-- 唯一键：uk_<table>_<columns>
-- 外键：fk_<table>_<ref_table>_<column>


-- ============================================================
-- realm_schema_init_global_v1.0.0.sql
-- 数据库初始化：字符集、排序规则、时区
-- ============================================================

CREATE DATABASE IF NOT EXISTS realm
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE realm;


-- ============================================================
-- 身份域-用户表；业务主表；包含完整审计字段、逻辑删除、乐观锁、多租户
-- ============================================================
DROP TABLE IF EXISTS `identity_user`;
CREATE TABLE `identity_user`
(
    `id`                  bigint      NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `user_type`           varchar(20) NOT NULL                COMMENT '用户归属域：INTERNAL-内部员工；EXTERNAL-外部会员（物理路由键）',
    `username`            varchar(64) NOT NULL                COMMENT '用户账号，租户内唯一',
    `password_hash`       varchar(255) NOT NULL               COMMENT '密码哈希',
    `display_name`        varchar(100) NOT NULL               COMMENT '用户显示名',
    `description`         varchar(500)         DEFAULT NULL   COMMENT '描述',
    `department_id`       bigint               DEFAULT NULL   COMMENT '部门ID，关联 identity_department.id',
    `email`               varchar(254)         DEFAULT NULL   COMMENT '用户邮箱',
    `phone_number`        varchar(32)          DEFAULT NULL   COMMENT '手机号码，建议 E.164 格式',
    `gender`              tinyint     NOT NULL DEFAULT 0      COMMENT '性别：0-未知；1-男；2-女',
    `avatar_url`          varchar(1024)        DEFAULT NULL   COMMENT '头像地址',
    `status`              tinyint     NOT NULL DEFAULT 0      COMMENT '账号状态：0-正常；1-停用',
    `last_login_ip`       varchar(45)          DEFAULT NULL   COMMENT '最后登录 IP（兼容 IPv6）',
    `last_login_at`       datetime(3)          DEFAULT NULL   COMMENT '最后登录时间（UTC）',
    `version`             int         NOT NULL DEFAULT 1      COMMENT '乐观锁版本号',
    `created_by`          bigint               DEFAULT NULL   COMMENT '创建人ID',
    `created_at`          datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by`          bigint               DEFAULT NULL   COMMENT '更新人ID',
    `updated_at`          datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    `deleted_at`          datetime(3) NULL DEFAULT NULL COMMENT '删除时间（UTC），NULL 表示未删除',
    `tenant_id`           bigint      NOT NULL DEFAULT 0      COMMENT '租户ID，0 表示全局默认，非 0 时关联 saas_tenant.id',

    `active_username`     varchar(64) GENERATED ALWAYS AS (
                              IF(`deleted_at` IS NULL, `username`, NULL)
                              ) STORED COMMENT '有效用户名，用于软删除下的唯一约束',
    `active_phone_number` varchar(32) GENERATED ALWAYS AS (
                              IF(`deleted_at` IS NULL AND `phone_number` IS NOT NULL AND `phone_number` <> '', `phone_number`, NULL)
                              ) STORED COMMENT '有效手机号，用于软删除下的唯一约束',
    `active_email`        varchar(254) GENERATED ALWAYS AS (
                              IF(`deleted_at` IS NULL AND `email` IS NOT NULL AND `email` <> '', `email`, NULL)
                              ) STORED COMMENT '有效邮箱，用于软删除下的唯一约束',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_identity_user_tenant_type_username_active` (`tenant_id`, `user_type`, `active_username`),
    UNIQUE KEY `uk_identity_user_tenant_type_phone_active`    (`tenant_id`, `user_type`, `active_phone_number`),
    UNIQUE KEY `uk_identity_user_tenant_type_email_active`    (`tenant_id`, `user_type`, `active_email`),
    KEY `idx_identity_user_department_id` (`department_id`),
    KEY `idx_identity_user_tenant_type_status` (`tenant_id`, `user_type`, `status`),
    KEY `idx_identity_user_created_at` (`created_at`),
    CONSTRAINT `chk_identity_user_user_type`
        CHECK (`user_type` IN ('INTERNAL', 'EXTERNAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='身份域-用户表';

-- ============================================================
-- 身份域-角色表；业务主表；包含完整审计字段、逻辑删除、乐观锁、多租户
-- ============================================================
DROP TABLE IF EXISTS `identity_role`;
CREATE TABLE `identity_role`
(
    `id`                 bigint       NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    `role_name`          varchar(100) NOT NULL COMMENT '角色名称',
    `role_code`          varchar(100) NOT NULL COMMENT '角色权限字符串，租户内唯一（如 super_admin）',
    `sort_order`         int          NOT NULL DEFAULT 0 COMMENT '显示顺序，数字越小越靠前',
    `data_scope`         tinyint      NOT NULL DEFAULT 1 COMMENT '数据范围：1-全部数据权限；2-自定义数据权限；3-本部门数据权限；4-本部门及以下数据权限；5-仅本人数据权限',
    `status`             tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0-正常；1-停用',
    `role_type`          tinyint      NOT NULL DEFAULT 2 COMMENT '角色类型：1-内置角色；2-自定义角色',
    `description`        varchar(500)          DEFAULT NULL COMMENT '描述',
    `version`            int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `created_by`         bigint                DEFAULT NULL COMMENT '创建人ID',
    `created_at`         datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by`         bigint                DEFAULT NULL COMMENT '更新人ID',
    `updated_at`         datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    `deleted_at`         datetime(3) NULL DEFAULT NULL COMMENT '删除时间（UTC），NULL表示未删除',
    `tenant_id`          bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认，非 0 时关联 saas_tenant.id',

    `active_role_code`   varchar(100) GENERATED ALWAYS AS (
                             IF(`deleted_at` IS NULL, `role_code`, NULL)
                             ) STORED COMMENT '有效角色编码，用于软删除下的唯一约束',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_identity_role_tenant_role_code_active` (`tenant_id`, `active_role_code`),
    KEY `idx_identity_role_tenant_status` (`tenant_id`, `status`),
    KEY `idx_identity_role_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='身份域-角色表';

-- ============================================================
-- 身份域-权限资源表；业务主表；统一目录、菜单、按钮、API、数据权限等资源；
-- 包含完整审计字段、逻辑删除、乐观锁；全局资源，无多租户
-- ============================================================
DROP TABLE IF EXISTS `identity_resource`;
CREATE TABLE `identity_resource`
(
    `id`                    bigint       NOT NULL AUTO_INCREMENT COMMENT '资源ID',
    `resource_name`         varchar(100) NOT NULL COMMENT '资源名称',
    `resource_code`         varchar(200) NOT NULL COMMENT '资源编码，如 system:user:query',
    `resource_type`         tinyint      NOT NULL COMMENT '资源类型：1-目录；2-菜单；3-按钮；4-API；5-数据权限',
    `sort_order`            int          NOT NULL DEFAULT 0 COMMENT '显示顺序，数字越小越靠前',
    `parent_id`             bigint       NOT NULL DEFAULT 0 COMMENT '父级ID，0 表示顶级',
    `path`                  varchar(255)          DEFAULT NULL COMMENT '路由地址，仅目录/菜单有效；NULL 表示不适用',
    `icon`                  varchar(100)          DEFAULT NULL COMMENT '菜单图标，仅目录/菜单有效；NULL 表示未设置',
    `component`             varchar(255)          DEFAULT NULL COMMENT '前端组件路径，仅目录/菜单有效',
    `component_name`        varchar(255)          DEFAULT NULL COMMENT '前端组件名，仅目录/菜单有效',
    `status`                tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0-正常；1-停用',
    `is_visible`            tinyint   NOT NULL DEFAULT 1 COMMENT '是否可见：0-否；1-是，仅目录/菜单有效',
    `is_keep_alive`         tinyint   NOT NULL DEFAULT 1 COMMENT '是否缓存：0-否；1-是，仅目录/菜单有效',
    `is_always_show`        tinyint   NOT NULL DEFAULT 1 COMMENT '是否总是显示：0-否；1-是，仅目录/菜单有效',
    `version`               int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `created_by`            bigint                DEFAULT NULL COMMENT '创建人ID',
    `created_at`            datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by`            bigint                DEFAULT NULL COMMENT '更新人ID',
    `updated_at`            datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    `deleted_at`            datetime(3) NULL DEFAULT NULL COMMENT '删除时间（UTC），NULL表示未删除',

    `active_resource_code`  varchar(200) GENERATED ALWAYS AS (
                                IF(`deleted_at` IS NULL AND `resource_code` <> '', `resource_code`, NULL)
                                ) STORED COMMENT '有效资源编码，用于软删除下的唯一约束',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_identity_resource_resource_code_active` (`active_resource_code`),
    KEY `idx_identity_resource_parent_id` (`parent_id`),
    KEY `idx_identity_resource_resource_type` (`resource_type`),
    KEY `idx_identity_resource_status` (`status`),
    CONSTRAINT `chk_identity_resource_resource_code_not_empty` CHECK (`resource_code` <> '')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='身份域-权限资源表';

-- ============================================================
-- 身份域-岗位表；业务主表；包含完整审计字段、逻辑删除、乐观锁、多租户
-- ============================================================
DROP TABLE IF EXISTS `identity_position`;
CREATE TABLE `identity_position`
(
    `id`                     bigint       NOT NULL AUTO_INCREMENT COMMENT '岗位ID',
    `position_code`          varchar(64)  NOT NULL COMMENT '岗位编码，租户内唯一（如 ENGINEER、MANAGER）',
    `position_name`          varchar(100) NOT NULL COMMENT '岗位名称',
    `sort_order`             int          NOT NULL DEFAULT 0 COMMENT '显示顺序，数字越小越靠前',
    `status`                 tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0-正常；1-停用',
    `description`            varchar(500)          DEFAULT NULL COMMENT '描述',
    `version`                int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `created_by`             bigint                DEFAULT NULL COMMENT '创建人ID',
    `created_at`             datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by`             bigint                DEFAULT NULL COMMENT '更新人ID',
    `updated_at`             datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    `deleted_at`             datetime(3) NULL DEFAULT NULL COMMENT '删除时间（UTC），NULL表示未删除',
    `tenant_id`              bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认，非 0 时关联 saas_tenant.id',

    `active_position_code`   varchar(64) GENERATED ALWAYS AS (
                                 IF(`deleted_at` IS NULL, `position_code`, NULL)
                                 ) STORED COMMENT '有效岗位编码，用于软删除下的唯一约束',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_identity_position_tenant_position_code_active` (`tenant_id`, `active_position_code`),
    KEY `idx_identity_position_tenant_status` (`tenant_id`, `status`),
    KEY `idx_identity_position_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='身份域-岗位表';

-- ============================================================
-- 身份域-部门表；业务主表；包含完整审计字段、逻辑删除、乐观锁、多租户
-- ============================================================
DROP TABLE IF EXISTS `identity_department`;
CREATE TABLE `identity_department`
(
    `id`                      bigint       NOT NULL AUTO_INCREMENT COMMENT '部门ID',
    `department_name`         varchar(100) NOT NULL COMMENT '部门名称',
    `parent_id`               bigint       NOT NULL DEFAULT 0 COMMENT '父级ID，0 表示顶级',
    `sort_order`              int          NOT NULL DEFAULT 0 COMMENT '显示顺序，数字越小越靠前',
    `manager_user_id`         bigint                DEFAULT NULL COMMENT '部门负责人用户ID，关联 identity_user.id',
    `phone_number`            varchar(32)           DEFAULT NULL COMMENT '联系电话',
    `email`                   varchar(254)          DEFAULT NULL COMMENT '邮箱',
    `status`                  tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0-正常；1-停用',
    `version`                 int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `created_by`              bigint                DEFAULT NULL COMMENT '创建人ID',
    `created_at`              datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by`              bigint                DEFAULT NULL COMMENT '更新人ID',
    `updated_at`              datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    `deleted_at`              datetime(3) NULL DEFAULT NULL COMMENT '删除时间（UTC），NULL表示未删除',
    `tenant_id`               bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认，非 0 时关联 saas_tenant.id',

    `active_department_name`  varchar(100) GENERATED ALWAYS AS (
                                  IF(`deleted_at` IS NULL, `department_name`, NULL)
                                  ) STORED COMMENT '有效部门名称，用于软删除下的唯一约束',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_identity_department_tenant_parent_name_active` (`tenant_id`, `parent_id`, `active_department_name`),
    KEY `idx_identity_department_parent_id` (`parent_id`),
    KEY `idx_identity_department_manager_user_id` (`manager_user_id`),
    KEY `idx_identity_department_tenant_status` (`tenant_id`, `status`),
    KEY `idx_identity_department_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='身份域-部门表';

-- ============================================================
-- 身份域-用户与角色关联表；多对多关联表；顶级企业标准：精简审计字段（仅 created_by/created_at），不搞逻辑删除，不搞乐观锁，物理删除；复合主键
-- ============================================================
DROP TABLE IF EXISTS `identity_user_role`;
CREATE TABLE `identity_user_role`
(
    `user_id`    bigint       NOT NULL COMMENT '用户ID，关联 identity_user.id',
    `role_id`    bigint       NOT NULL COMMENT '角色ID，关联 identity_role.id',
    `tenant_id`  bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认，非 0 时关联 saas_tenant.id',
    `created_by` bigint                DEFAULT NULL COMMENT '创建人ID',
    `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    PRIMARY KEY (`tenant_id`, `user_id`, `role_id`),
    KEY `idx_identity_user_role_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='身份域-用户与角色关联表';

-- ============================================================
-- 身份域-用户与岗位关联表；多对多关联表；顶级企业标准：精简审计字段（仅 created_by/created_at），不搞逻辑删除，不搞乐观锁，物理删除；复合主键
-- ============================================================
DROP TABLE IF EXISTS `identity_user_position`;
CREATE TABLE `identity_user_position`
(
    `user_id`     bigint       NOT NULL COMMENT '用户ID，关联 identity_user.id',
    `position_id` bigint       NOT NULL COMMENT '岗位ID，关联 identity_position.id',
    `tenant_id`   bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认，非 0 时关联 saas_tenant.id',
    `created_by`  bigint                DEFAULT NULL COMMENT '创建人ID',
    `created_at`  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    PRIMARY KEY (`tenant_id`, `user_id`, `position_id`),
    KEY `idx_identity_user_position_position_id` (`position_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='身份域-用户与岗位关联表';

-- ============================================================
-- 身份域-角色与部门关联表；多对多关联表；顶级企业标准：精简审计字段（仅 created_by/created_at），不搞逻辑删除，不搞乐观锁，物理删除；复合主键
-- ============================================================
DROP TABLE IF EXISTS `identity_role_department`;
CREATE TABLE `identity_role_department`
(
    `role_id`       bigint       NOT NULL COMMENT '角色ID，关联 identity_role.id',
    `department_id` bigint       NOT NULL COMMENT '部门ID，关联 identity_department.id',
    `tenant_id`     bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认，非 0 时关联 saas_tenant.id',
    `created_by`    bigint                DEFAULT NULL COMMENT '创建人ID',
    `created_at`    datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    PRIMARY KEY (`tenant_id`, `role_id`, `department_id`),
    KEY `idx_identity_role_department_department_id` (`department_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='身份域-角色与部门关联表';

-- ============================================================
-- 身份域-角色与权限资源关联表；多对多关联表；顶级企业标准：精简审计字段（仅 created_by/created_at），不搞逻辑删除，不搞乐观锁，物理删除；复合主键
-- ============================================================
DROP TABLE IF EXISTS `identity_role_resource`;
CREATE TABLE `identity_role_resource`
(
    `role_id`     bigint       NOT NULL COMMENT '角色ID，关联 identity_role.id',
    `resource_id` bigint       NOT NULL COMMENT '资源ID，关联 identity_resource.id',
    `tenant_id`   bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认，非 0 时关联 saas_tenant.id',
    `created_by`  bigint                DEFAULT NULL COMMENT '创建人ID',
    `created_at`  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    PRIMARY KEY (`tenant_id`, `role_id`, `resource_id`),
    KEY `idx_identity_role_resource_resource_id` (`resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='身份域-角色与权限资源关联表';


-- ============================================================
-- SaaS 域-租户表；业务主表；多租户体系根表；包含完整审计字段、逻辑删除、乐观锁；
-- 注意：本表自身不再有 tenant_id，它本身就是租户
-- ============================================================
DROP TABLE IF EXISTS `saas_tenant`;
CREATE TABLE `saas_tenant`
(
    `id`                   bigint       NOT NULL AUTO_INCREMENT COMMENT '租户ID',
    `tenant_code`          varchar(64)  NOT NULL COMMENT '租户编码，全局唯一',
    `tenant_name`          varchar(100) NOT NULL COMMENT '租户名',
    `contact_user_id`      bigint                DEFAULT NULL COMMENT '联系人用户ID，关联 identity_user.id',
    `contact_name`         varchar(100) NOT NULL COMMENT '联系人姓名',
    `contact_phone_number` varchar(32)           DEFAULT NULL COMMENT '联系人手机号',
    `contact_email`        varchar(254)          DEFAULT NULL COMMENT '联系人邮箱',
    `status`               tinyint      NOT NULL DEFAULT 0 COMMENT '租户状态：0-正常；1-停用；2-过期',
    `package_id`           bigint       NOT NULL COMMENT '租户套餐编号，关联 saas_tenant_package.id',
    `expires_at`           datetime(3) NOT NULL COMMENT '过期时间（UTC）',
    `max_account_count`    int          NOT NULL DEFAULT 0 COMMENT '最大账号数量，0-不限制',
    `version`              int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `created_by`           bigint                DEFAULT NULL COMMENT '创建人ID',
    `created_at`           datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by`           bigint                DEFAULT NULL COMMENT '更新人ID',
    `updated_at`           datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    `deleted_at`           datetime(3) NULL DEFAULT NULL COMMENT '删除时间（UTC），NULL 表示未删除',

    `active_tenant_code`   varchar(64) GENERATED ALWAYS AS (
                               IF(`deleted_at` IS NULL, `tenant_code`, NULL)
                               ) STORED COMMENT '有效租户编码，用于软删除下的唯一约束',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_saas_tenant_tenant_code_active` (`active_tenant_code`),
    KEY `idx_saas_tenant_package_id` (`package_id`),
    KEY `idx_saas_tenant_status_expires_at` (`status`, `expires_at`),
    KEY `idx_saas_tenant_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SaaS 域-租户表';

-- ============================================================
-- SaaS 域-租户域名表；业务从属表；跟随租户生命周期；精简审计字段（仅 created_by/created_at），
-- 不搞逻辑删除，不搞乐观锁；租户删除时物理级联删除
-- ============================================================
DROP TABLE IF EXISTS `saas_tenant_domain`;
CREATE TABLE `saas_tenant_domain`
(
    `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`         bigint       NOT NULL COMMENT '租户ID，0 表示全局默认，非 0 时关联 saas_tenant.id',
    `domain`            varchar(255) NOT NULL COMMENT '域名',
    `is_primary`        tinyint   NOT NULL DEFAULT 0 COMMENT '是否主域名：0-否；1-是',
    `created_by`        bigint                DEFAULT NULL COMMENT '创建人ID',
    `created_at`        datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',

    `primary_tenant_id` bigint GENERATED ALWAYS AS (
                            IF(`is_primary` = 1, `tenant_id`, NULL)
                            ) STORED COMMENT '主域名租户ID，用于保证每个租户只有一个主域名',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_saas_tenant_domain_domain` (`domain`),
    UNIQUE KEY `uk_saas_tenant_domain_primary_tenant` (`primary_tenant_id`),
    KEY `idx_saas_tenant_domain_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SaaS 域-租户域名表';

-- ============================================================
-- SaaS 域-租户套餐表；业务主表；全局套餐，由平台管理，无多租户；
-- 包含完整审计字段、逻辑删除、乐观锁
-- ============================================================
DROP TABLE IF EXISTS `saas_tenant_package`;
CREATE TABLE `saas_tenant_package`
(
    `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '套餐编号',
    `package_name`  varchar(100) NOT NULL COMMENT '套餐名',
    `status`        tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0-正常；1-停用',
    `description`   varchar(500)          DEFAULT NULL COMMENT '描述',
    `version`       int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `created_by`    bigint                DEFAULT NULL COMMENT '创建人ID',
    `created_at`    datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by`    bigint                DEFAULT NULL COMMENT '更新人ID',
    `updated_at`    datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    `deleted_at`    datetime(3) NULL DEFAULT NULL COMMENT '删除时间（UTC），NULL表示未删除',

    PRIMARY KEY (`id`),
    KEY `idx_saas_tenant_package_status` (`status`),
    KEY `idx_saas_tenant_package_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SaaS 域-租户套餐表';

-- ============================================================
-- SaaS 域-租户套餐与权限资源关联表；多对多关联表；顶级企业标准：精简审计字段（仅 created_by/created_at），
-- 不搞逻辑删除，不搞乐观锁，物理删除；复合主键
-- 说明：原 saas_tenant_package_menu 已改名，因 identity_menu 已合并为 identity_resource
-- ============================================================
DROP TABLE IF EXISTS `saas_tenant_package_resource`;
CREATE TABLE `saas_tenant_package_resource`
(
    `package_id`  bigint       NOT NULL COMMENT '套餐编号，关联 saas_tenant_package.id',
    `resource_id` bigint       NOT NULL COMMENT '资源ID，关联 identity_resource.id',
    `created_by`  bigint                DEFAULT NULL COMMENT '创建人ID',
    `created_at`  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    PRIMARY KEY (`package_id`, `resource_id`),
    KEY `idx_saas_tenant_package_resource_resource_id` (`resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SaaS 域-租户套餐与权限资源关联表';

-- ============================================================
-- 基础设施域-参数配置表；业务主表；支持全局默认（tenant_id=0）与租户覆盖；
-- 包含完整审计字段、逻辑删除、乐观锁、多租户
-- ============================================================
DROP TABLE IF EXISTS `infra_config`;
CREATE TABLE `infra_config`
(
    `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `category`       varchar(64)  NOT NULL COMMENT '参数分组（如 biz、url、system）',
    `config_type`    tinyint      NOT NULL COMMENT '参数类型：1-系统内置；2-自定义',
    `config_name`    varchar(100) NOT NULL COMMENT '参数名称（展示用）',
    `config_key`     varchar(100) NOT NULL COMMENT '参数键名（如 system.user.init-password）',
    `config_value`   varchar(2048) NOT NULL COMMENT '参数键值',
    `is_visible`     tinyint   NOT NULL DEFAULT 1 COMMENT '是否可见：0-不可见；1-可见',
    `description`    varchar(500)          DEFAULT NULL COMMENT '描述',
    `version`        int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `created_by`     bigint                DEFAULT NULL COMMENT '创建人ID',
    `created_at`     datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by`     bigint                DEFAULT NULL COMMENT '更新人ID',
    `updated_at`     datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    `deleted_at`     datetime(3) NULL DEFAULT NULL COMMENT '删除时间（UTC），NULL表示未删除',
    `tenant_id`      bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认，非 0 时关联 saas_tenant.id',

    `active_config_key` varchar(100) GENERATED ALWAYS AS (
                         IF(`deleted_at` IS NULL AND `config_key` <> '', `config_key`, NULL)
                         ) STORED COMMENT '有效参数键名，用于软删除下的唯一约束',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_infra_config_tenant_config_key_active` (`tenant_id`, `active_config_key`),
    KEY `idx_infra_config_category` (`category`),
    KEY `idx_infra_config_config_type` (`config_type`),
    KEY `idx_infra_config_tenant_id` (`tenant_id`),
    KEY `idx_infra_config_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基础设施域-参数配置表';

-- ============================================================
-- 基础设施域-文件存储配置表；业务主表；全局配置，由平台管理，无多租户；
-- 包含完整审计字段、逻辑删除、乐观锁
-- ============================================================
DROP TABLE IF EXISTS `infra_file_config`;
CREATE TABLE `infra_file_config`
(
    `id`               bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `config_name`      varchar(100) NOT NULL COMMENT '配置名称（展示用）',
    `storage_type`     tinyint      NOT NULL COMMENT '存储类型：1-数据库；10-本地磁盘；11-FTP；12-SFTP；20-S3 对象存储（含七牛云、阿里云 OSS、腾讯云 COS、MinIO）',
    `config_encrypted` varbinary(8192) NOT NULL COMMENT '加密后的存储配置（JSON 格式，含密钥等敏感信息）',
    `is_default`       tinyint   NOT NULL DEFAULT 0 COMMENT '是否为默认配置：0-否；1-是（全局只能有一个）',
    `status`           tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0-启用；1-禁用',
    `description`      varchar(500)          DEFAULT NULL COMMENT '描述',
    `version`          int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `created_by`       bigint                DEFAULT NULL COMMENT '创建人ID',
    `created_at`       datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by`       bigint                DEFAULT NULL COMMENT '更新人ID',
    `updated_at`       datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    `deleted_at`       datetime(3) NULL DEFAULT NULL COMMENT '删除时间（UTC），NULL表示未删除',

    `default_marker`   tinyint GENERATED ALWAYS AS (
        IF(`is_default` = 1 AND `deleted_at` IS NULL, 1, NULL)
        ) STORED COMMENT '默认配置标记（is_default=1 且未删除时值为 1，用于唯一约束）',
    `active_config_name` varchar(100) GENERATED ALWAYS AS (
                           IF(`deleted_at` IS NULL, `config_name`, NULL)
                           ) STORED COMMENT '有效配置名称，用于软删除下的唯一约束',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_infra_file_config_default_marker` (`default_marker`),
    UNIQUE KEY `uk_infra_file_config_config_name_active` (`active_config_name`),
    KEY `idx_infra_file_config_storage_type` (`storage_type`),
    KEY `idx_infra_file_config_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基础设施域-文件存储配置表';

-- ============================================================
-- 基础设施域-文件元数据表；业务主表；按租户隔离；包含完整审计字段、逻辑删除、多租户
-- ============================================================
DROP TABLE IF EXISTS `infra_file`;
CREATE TABLE `infra_file`
(
    `id`               bigint        NOT NULL AUTO_INCREMENT COMMENT '文件ID',
    `file_config_id` bigint NOT NULL DEFAULT 0 COMMENT '文件配置ID，0 表示默认配置；关联 infra_file_config.id',
    `original_name`    varchar(255)  NOT NULL COMMENT '原始文件名（用户上传时的文件名，如 photo.jpg）',
    `storage_path`     varchar(512)  NOT NULL COMMENT '存储路径（文件配置下的相对路径，如 /2026/09/17/abc123.jpg）',
    `url`              varchar(1024) NOT NULL COMMENT '文件访问 URL',
    `content_type`     varchar(128)           DEFAULT NULL COMMENT '文件 MIME 类型（如 image/jpeg、application/pdf）',
    `size_bytes`       bigint  NOT NULL COMMENT '文件大小（字节）',
    `sha256`           varchar(64)            DEFAULT NULL COMMENT '文件内容的 SHA-256 哈希，用于秒传和去重',
    `created_by`       bigint                 DEFAULT NULL COMMENT '上传人ID',
    `created_at`       datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '上传时间（UTC）',
    `updated_at`       datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    `deleted_at`       datetime(3) NULL DEFAULT NULL COMMENT '删除时间（UTC），NULL表示未删除',
    `tenant_id`        bigint        NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认，非 0 时关联 saas_tenant.id',

    `active_storage_path` varchar(512) GENERATED ALWAYS AS (
                           IF(`deleted_at` IS NULL, `storage_path`, NULL)
                           ) STORED COMMENT '有效存储路径，用于软删除下的唯一约束',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_infra_file_tenant_config_path_active` (`tenant_id`, `file_config_id`, `active_storage_path`),
    KEY `idx_infra_file_tenant_id` (`tenant_id`),
    KEY `idx_infra_file_sha256` (`sha256`),
    KEY `idx_infra_file_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基础设施域-文件元数据表';

-- ============================================================
-- 基础设施域-文件内容表；大字段表；顶级企业标准：二进制内容体积大，
-- 逻辑删除会造成巨大浪费，推荐物理删除；只保留创建时间，不搞乐观锁、不搞逻辑删除
-- ============================================================
DROP TABLE IF EXISTS `infra_file_content`;
CREATE TABLE `infra_file_content`
(
    `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `file_config_id` bigint       NOT NULL COMMENT '文件配置ID，关联 infra_file_config.id',
    `path`           varchar(512) NOT NULL COMMENT '文件路径（文件配置下的相对路径）',
    `content`        mediumblob   NOT NULL COMMENT '文件内容（二进制，最大 16MB）',
    `created_at`     datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_infra_file_content_config_path` (`file_config_id`, `path`),
    KEY `idx_infra_file_content_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基础设施域-文件内容表';

-- ============================================================
-- 基础设施域-数据源配置表；业务主表；用于代码生成器读取多个数据库；全局配置，无多租户；
-- 包含完整审计字段、逻辑删除、乐观锁；数据库密码加密存储
-- ============================================================
DROP TABLE IF EXISTS `infra_data_source_config`;
CREATE TABLE `infra_data_source_config`
(
    `id`                          bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `data_source_name`            varchar(100)  NOT NULL COMMENT '数据源名称（展示用）',
    `database_type`               varchar(32)   NOT NULL COMMENT '数据库类型：MySQL、PostgreSQL、Oracle、SQLServer 等',
    `jdbc_url`                    varchar(1024) NOT NULL COMMENT 'JDBC 连接串',
    `database_username`           varchar(255)  NOT NULL COMMENT '数据库用户名',
    `database_password_encrypted` varbinary(512) NOT NULL COMMENT '加密后的数据库密码',
    `status`                      tinyint       NOT NULL DEFAULT 0 COMMENT '状态：0-启用；1-禁用',
    `description`                 varchar(500)           DEFAULT NULL COMMENT '描述',
    `version`                     int           NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `created_by`                  bigint                 DEFAULT NULL COMMENT '创建人ID',
    `created_at`                  datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by`                  bigint                 DEFAULT NULL COMMENT '更新人ID',
    `updated_at`                  datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    `deleted_at`                  datetime(3) NULL DEFAULT NULL COMMENT '删除时间（UTC），NULL表示未删除',

    `active_data_source_name`     varchar(100) GENERATED ALWAYS AS (
                                      IF(`deleted_at` IS NULL, `data_source_name`, NULL)
                                      ) STORED COMMENT '有效数据源名称，用于软删除下的唯一约束',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_infra_data_source_config_name_active` (`active_data_source_name`),
    KEY `idx_infra_data_source_config_database_type` (`database_type`),
    KEY `idx_infra_data_source_config_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基础设施域-数据源配置表';

-- ============================================================
-- 基础设施域-定时任务配置表；业务主表；全局配置，无多租户；
-- 包含完整审计字段、逻辑删除、乐观锁
-- ============================================================
DROP TABLE IF EXISTS `infra_scheduled_job`;
CREATE TABLE `infra_scheduled_job`
(
    `id`                 bigint       NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    `job_name`           varchar(100) NOT NULL COMMENT '任务名称',
    `status`             tinyint      NOT NULL DEFAULT 0 COMMENT '任务状态：0-初始化中；1-运行中；2-暂停',
    `handler_name`       varchar(128) NOT NULL COMMENT '任务处理器名称（如 payNotifyJob）',
    `handler_parameters` varchar(512)          DEFAULT NULL COMMENT '任务处理器参数',
    `cron_expression`    varchar(64)  NOT NULL COMMENT 'CRON 表达式（如 0 0 2 * * ?）',
    `retry_count`        int  NOT NULL DEFAULT 0 COMMENT '重试次数，0-不重试',
    `retry_interval_ms`  int  NOT NULL DEFAULT 0 COMMENT '重试间隔（毫秒），0-不重试',
    `monitor_timeout_ms` int  NOT NULL DEFAULT 0 COMMENT '监控超时时间（毫秒），0-不监控',
    `version`            int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `created_by`         bigint                DEFAULT NULL COMMENT '创建人ID',
    `created_at`         datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by`         bigint                DEFAULT NULL COMMENT '更新人ID',
    `updated_at`         datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    `deleted_at`         datetime(3) NULL DEFAULT NULL COMMENT '删除时间（UTC），NULL表示未删除',

    `active_job_name`    varchar(100) GENERATED ALWAYS AS (
                             IF(`deleted_at` IS NULL, `job_name`, NULL)
                             ) STORED COMMENT '有效任务名称，用于软删除下的唯一约束',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_infra_scheduled_job_job_name_active` (`active_job_name`),
    KEY `idx_infra_scheduled_job_status` (`status`),
    KEY `idx_infra_scheduled_job_handler_name` (`handler_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基础设施域-定时任务配置表';

-- ============================================================
-- 基础设施域-定时任务执行日志表；日志表；只增不改、数据量大；
-- 顶级企业标准：只保留创建时间，不搞逻辑删除、不搞乐观锁；建议按月分区或归档
-- ============================================================
DROP TABLE IF EXISTS `infra_scheduled_job_log`;
CREATE TABLE `infra_scheduled_job_log`
(
    `id`                 bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `job_id`             bigint       NOT NULL COMMENT '定时任务ID，关联 infra_scheduled_job.id',
    `handler_name`       varchar(128) NOT NULL COMMENT '任务处理器名称（快照）',
    `handler_parameters` varchar(512)          DEFAULT NULL COMMENT '任务处理器参数（快照）',
    `execution_index`    tinyint  NOT NULL DEFAULT 1 COMMENT '第几次执行（用于重试场景，从 1 开始）',
    `started_at`         datetime(3) NOT NULL COMMENT '开始执行时间（UTC）',
    `ended_at`           datetime(3) NULL DEFAULT NULL COMMENT '结束执行时间（UTC）',
    `duration_ms`        int  DEFAULT NULL COMMENT '执行时长（毫秒）',
    `status`             tinyint      NOT NULL COMMENT '执行状态：0-运行中；1-成功；2-失败',
    `result_data`        text                  DEFAULT NULL COMMENT '结果数据（成功时为返回值，失败时为异常信息）',
    `created_at`         datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    PRIMARY KEY (`id`),
    KEY `idx_infra_scheduled_job_log_job_id_created_at` (`job_id`, `created_at`),
    KEY `idx_infra_scheduled_job_log_status_created_at` (`status`, `created_at`),
    KEY `idx_infra_scheduled_job_log_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基础设施域-定时任务执行日志表';

-- ============================================================
-- 基础设施域-API 访问日志表；日志表；只增不改、数据量大；按租户隔离；
-- 顶级企业标准：只保留创建时间，不搞逻辑删除、不搞乐观锁；建议按月分区或归档
-- ============================================================
DROP TABLE IF EXISTS `infra_api_access_log`;
CREATE TABLE `infra_api_access_log`
(
    `id`                 bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `trace_id`           varchar(64)            DEFAULT NULL COMMENT '链路追踪ID，NULL 表示未采集',
    `user_id`   bigint      NULL DEFAULT NULL COMMENT '用户ID，NULL 表示未登录或系统行为',
    `user_type` varchar(20) NULL DEFAULT NULL COMMENT '用户归属域：INTERNAL-内部员工；EXTERNAL-外部会员；NULL 表示未登录',
    `application_name`   varchar(128)  NOT NULL COMMENT '应用名（微服务名）',
    `ip_address`         varchar(45)   NOT NULL COMMENT '请求来源 IP（兼容 IPv6）',
    `user_agent`         varchar(1024) NOT NULL COMMENT '客户端 User-Agent',
    `http_method`        varchar(16)            DEFAULT NULL COMMENT 'HTTP 方法：GET、POST、PUT、DELETE 等；NULL 表示未采集',
    `request_url`        varchar(2048)          DEFAULT NULL COMMENT '请求 URL；NULL 表示未采集',
    `request_parameters` mediumtext             DEFAULT NULL COMMENT '请求参数（JSON 格式或表单数据，需脱敏）',
    `response_body`      mediumtext             DEFAULT NULL COMMENT '响应结果（JSON 格式，可根据配置截断，需脱敏）',
    `operation_module`   varchar(64)            DEFAULT NULL COMMENT '操作模块（如：订单管理、用户管理）',
    `operation_name`     varchar(64)            DEFAULT NULL COMMENT '操作名称（如：创建订单、删除用户）',
    `operation_type`     tinyint                DEFAULT NULL COMMENT '操作类型：0-其它；1-查询；2-新增；3-修改；4-删除；5-导出；6-导入',
    `started_at`         datetime(3)  NOT NULL COMMENT '请求开始时间（UTC）',
    `ended_at`           datetime(3)  NOT NULL COMMENT '请求结束时间（UTC）',
    `duration_ms`        int  NOT NULL COMMENT '执行时长（毫秒）',
    `http_status_code`   int           NOT NULL DEFAULT 0 COMMENT 'HTTP 状态码（如 200、404、500）',
    `result_code`        int           NOT NULL DEFAULT 0 COMMENT '业务结果码，0 表示成功，非 0 表示业务异常',
    `result_message`     varchar(512)           DEFAULT NULL COMMENT '业务结果提示；NULL 表示无提示',
    `created_at`         datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `tenant_id`          bigint        NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认，非 0 时关联 saas_tenant.id',
    PRIMARY KEY (`id`),
    KEY `idx_infra_api_access_log_trace_id` (`trace_id`),
    KEY `idx_infra_api_access_log_user_id_created_at` (`user_id`, `created_at`),
    KEY `idx_infra_api_access_log_tenant_id_created_at` (`tenant_id`, `created_at`),
    KEY `idx_infra_api_access_log_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基础设施域-API 访问日志表';

-- ============================================================
-- 基础设施域-API 错误日志表；日志表；只增不改、数据量大；按租户隔离；
-- 顶级企业标准：只保留创建时间，不搞逻辑删除、不搞乐观锁；建议按月分区或归档；
-- 异常堆栈需脱敏，避免记录敏感信息
-- ============================================================
DROP TABLE IF EXISTS `infra_api_error_log`;
CREATE TABLE `infra_api_error_log`
(
    `id`                           bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `trace_id`                     varchar(64)   NOT NULL COMMENT '链路追踪ID',
    `user_id`   bigint      NULL DEFAULT NULL COMMENT '用户ID，NULL 表示未登录或系统行为',
    `user_type` varchar(20) NULL DEFAULT NULL COMMENT '用户归属域：INTERNAL-内部员工；EXTERNAL-外部会员；NULL 表示未登录',
    `application_name`             varchar(128)  NOT NULL COMMENT '应用名（微服务名）',
    `ip_address`                   varchar(45)   NOT NULL COMMENT '请求来源 IP（兼容 IPv6）',
    `user_agent`                   varchar(1024) NOT NULL COMMENT '客户端 User-Agent',
    `http_method`                  varchar(16)   NOT NULL COMMENT 'HTTP 方法：GET、POST、PUT、DELETE 等',
    `request_url`                  varchar(2048) NOT NULL COMMENT '请求 URL',
    `request_parameters`           mediumtext             DEFAULT NULL COMMENT '请求参数（JSON 格式或表单数据，需脱敏）',
    `exception_name`               varchar(255)           DEFAULT NULL COMMENT '异常类简称（如 NullPointerException），NULL 表示未采集',
    `exception_message`            text          NOT NULL COMMENT '异常消息（getMessage() 的返回值）',
    `exception_root_cause_message` text          NOT NULL COMMENT '异常根因消息（递归 cause 的最深层消息）',
    `exception_stack_trace`        mediumtext    NOT NULL COMMENT '异常堆栈轨迹（完整 stack trace）',
    `exception_class_name`         varchar(512)  NOT NULL COMMENT '异常发生的类全名',
    `exception_file_name`          varchar(512)  NOT NULL COMMENT '异常发生的源文件名',
    `exception_method_name`        varchar(512)  NOT NULL COMMENT '异常发生的方法名',
    `exception_line_number`        int           NOT NULL COMMENT '异常发生的源码行号',
    `exception_at`                 datetime(3)  NOT NULL COMMENT '异常发生时间（UTC）',
    `process_status`               tinyint       NOT NULL DEFAULT 0 COMMENT '处理状态：0-未处理；1-已处理；2-已忽略',
    `processed_at`                 datetime(3) NULL DEFAULT NULL COMMENT '处理时间（UTC）',
    `processed_by`                 bigint                 DEFAULT NULL COMMENT '处理人ID',
    `created_at`                   datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_at`                   datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    `tenant_id`                    bigint        NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认，非 0 时关联 saas_tenant.id',
    PRIMARY KEY (`id`),
    KEY `idx_infra_api_error_log_trace_id` (`trace_id`),
    KEY `idx_infra_api_error_log_process_status_created_at` (`process_status`, `created_at`),
    KEY `idx_infra_api_error_log_user_id_created_at` (`user_id`, `created_at`),
    KEY `idx_infra_api_error_log_exception_name_created_at` (`exception_name`, `created_at`),
    KEY `idx_infra_api_error_log_tenant_id_created_at` (`tenant_id`, `created_at`),
    KEY `idx_infra_api_error_log_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基础设施域-API 错误日志表';

-- ============================================================
-- 基础设施域-代码生成表定义；业务主表；全局配置，无多租户；
-- 包含完整审计字段、逻辑删除、乐观锁
-- 说明：parent_menu_id 已改为 parent_resource_id，因 identity_menu 已合并为 identity_resource
-- ============================================================
DROP TABLE IF EXISTS `infra_codegen_table`;
CREATE TABLE `infra_codegen_table`
(
    `id`                    bigint           NOT NULL AUTO_INCREMENT COMMENT '代码生成表ID',
    `data_source_config_id` bigint           NOT NULL COMMENT '数据源配置ID，关联 infra_data_source_config.id',
    `scene`                 tinyint  NOT NULL DEFAULT 1 COMMENT '生成场景：1-管理后台；2-用户APP',
    `table_name`            varchar(200)     NOT NULL COMMENT '表名称',
    `table_comment`         varchar(500)              DEFAULT NULL COMMENT '表描述，NULL 表示无描述',
    `description`           varchar(500)              DEFAULT NULL COMMENT '描述',
    `module_name`           varchar(64)      NOT NULL COMMENT '模块名',
    `business_name`         varchar(64)      NOT NULL COMMENT '业务名',
    `class_name`            varchar(100)     NOT NULL COMMENT '类名称',
    `class_comment`         varchar(100)     NOT NULL COMMENT '类描述',
    `author`                varchar(64)      NOT NULL COMMENT '作者',
    `template_type`         tinyint  NOT NULL DEFAULT 1 COMMENT '模板类型：1-单表；2-树表；3-主子表（标准）；4-主子表（ERP）；5-主子表（内嵌）',
    `front_type`            tinyint  NOT NULL COMMENT '前端类型：10-Vue2 Element UI；20-Vue3 Element Plus；30-Vben2.0 Ant Design；40-Vben5.0 Ant Design Schema；41-Vben5.0 Ant Design 标准；50-Vben5.0 Element Plus Schema；51-Vben5.0 Element Plus 标准；60-Admin Uniapp 移动端',
    `parent_resource_id`    bigint                    DEFAULT NULL COMMENT '父资源ID，关联 identity_resource.id',
    `master_table_id`       bigint                    DEFAULT NULL COMMENT '主表ID，关联 infra_codegen_table.id',
    `sub_join_column_id`    bigint                    DEFAULT NULL COMMENT '子表关联主表的字段ID，关联 infra_codegen_column.id',
    `is_sub_join_many`      tinyint       NOT NULL DEFAULT 0 COMMENT '主表与子表是否一对多：0-否；1-是',
    `tree_parent_column_id` bigint                    DEFAULT NULL COMMENT '树表的父字段ID，关联 infra_codegen_column.id',
    `tree_name_column_id`   bigint                    DEFAULT NULL COMMENT '树表的名字字段ID，关联 infra_codegen_column.id',
    `version`               int              NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `created_by`            bigint                    DEFAULT NULL COMMENT '创建人ID',
    `created_at`            datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by`            bigint                    DEFAULT NULL COMMENT '更新人ID',
    `updated_at`            datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    `deleted_at`            datetime(3)     NULL     DEFAULT NULL COMMENT '删除时间（UTC），NULL表示未删除',

    `active_table_name`     varchar(200) GENERATED ALWAYS AS (
                                IF(`deleted_at` IS NULL AND `table_name` <> '', `table_name`, NULL)
                                ) STORED COMMENT '有效表名，用于软删除下的唯一约束',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_infra_codegen_table_data_source_table_active` (`data_source_config_id`, `active_table_name`),
    KEY `idx_infra_codegen_table_parent_resource_id` (`parent_resource_id`),
    KEY `idx_infra_codegen_table_master_table_id` (`master_table_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基础设施域-代码生成表定义';

-- ============================================================
-- 基础设施域-代码生成字段定义；业务主表；全局配置，无多租户；
-- 包含完整审计字段、逻辑删除、乐观锁
-- ============================================================
DROP TABLE IF EXISTS `infra_codegen_column`;
CREATE TABLE `infra_codegen_column`
(
    `id`                       bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `codegen_table_id`         bigint       NOT NULL COMMENT '代码生成表ID，关联 infra_codegen_table.id',
    `column_name`              varchar(200) NOT NULL COMMENT '数据库字段名（如 user_name）',
    `data_type`                varchar(100) NOT NULL COMMENT '数据库字段类型（如 varchar(64)、bigint）',
    `column_comment`           varchar(500) NOT NULL COMMENT '数据库字段描述',
    `is_nullable`              tinyint   NOT NULL DEFAULT 0 COMMENT '是否允许为空：0-否；1-是',
    `is_primary_key`           tinyint   NOT NULL DEFAULT 0 COMMENT '是否主键：0-否；1-是',
    `ordinal_position`         int          NOT NULL COMMENT '字段在表中的序号（从 1 开始）',
    `java_type`                varchar(64)  NOT NULL COMMENT 'Java 属性类型（如 String、Long、LocalDateTime）',
    `java_field`               varchar(128) NOT NULL COMMENT 'Java 属性名（驼峰，如 userName）',
    `dictionary_type_code`     varchar(200)          DEFAULT NULL COMMENT '字典类型编码（如 system_user_sex）',
    `example_value`            varchar(255)          DEFAULT NULL COMMENT '数据示例（用于生成测试数据）',
    `is_create_operation`      tinyint   NOT NULL DEFAULT 1 COMMENT '是否为 Create 创建操作的字段：0-否；1-是',
    `is_update_operation`      tinyint   NOT NULL DEFAULT 1 COMMENT '是否为 Update 更新操作的字段：0-否；1-是',
    `is_list_operation`        tinyint   NOT NULL DEFAULT 1 COMMENT '是否为 List 查询操作的字段：0-否；1-是',
    `list_operation_condition` varchar(32)  NOT NULL DEFAULT '=' COMMENT 'List 查询操作的条件类型：=、!=、>、>=、<、<=、LIKE、BETWEEN、IN 等',
    `is_list_operation_result` tinyint   NOT NULL DEFAULT 1 COMMENT '是否为 List 查询操作的返回字段：0-否；1-是',
    `html_type`                varchar(32)  NOT NULL COMMENT '前端显示类型：input、textarea、select、radio、checkbox、datetime、imageUpload、fileUpload 等',
    `version`                  int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `created_by`               bigint                DEFAULT NULL COMMENT '创建人ID',
    `created_at`               datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by`               bigint                DEFAULT NULL COMMENT '更新人ID',
    `updated_at`               datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    `deleted_at`               datetime(3) NULL DEFAULT NULL COMMENT '删除时间（UTC），NULL表示未删除',

    `active_column_name`       varchar(200) GENERATED ALWAYS AS (
                                   IF(`deleted_at` IS NULL, `column_name`, NULL)
                                   ) STORED COMMENT '有效字段名，用于软删除下的唯一约束',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_infra_codegen_column_table_column_active` (`codegen_table_id`, `active_column_name`),
    KEY `idx_infra_codegen_column_codegen_table_id` (`codegen_table_id`, `ordinal_position`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基础设施域-代码生成字段定义';


-- ============================================================
-- 消息域-短信模板表；业务主表；按租户隔离；包含完整审计字段、逻辑删除、乐观锁、多租户
-- ============================================================
DROP TABLE IF EXISTS `messaging_sms_template`;
CREATE TABLE `messaging_sms_template`
(
    `id`                   bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `template_code`        varchar(64)   NOT NULL COMMENT '模板编码，租户内唯一',
    `template_name`        varchar(100)  NOT NULL COMMENT '模板名称',
    `template_type`        tinyint       NOT NULL COMMENT '模板类型：1-验证码；2-通知；3-营销',
    `channel_id`           bigint        NOT NULL COMMENT '短信渠道ID，关联 messaging_sms_channel.id',
    `channel_code`         varchar(64)   NOT NULL COMMENT '短信渠道编码（冗余，方便查询）',
    `provider_template_id` varchar(64)   NOT NULL COMMENT '第三方平台的模板编号',
    `content`              varchar(1024) NOT NULL COMMENT '模板内容，使用 {key} 占位',
    `params`               json                   DEFAULT NULL COMMENT '模板参数数组',
    `status`               tinyint       NOT NULL DEFAULT 0 COMMENT '状态：0-启用；1-禁用',
    `description`          varchar(500)           DEFAULT NULL COMMENT '描述',
    `version`              int           NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `created_by`           bigint                 DEFAULT NULL COMMENT '创建人ID',
    `created_at`           datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by`           bigint                 DEFAULT NULL COMMENT '更新人ID',
    `updated_at`           datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    `deleted_at`           datetime(3) NULL DEFAULT NULL COMMENT '删除时间（UTC），NULL表示未删除',
    `tenant_id`            bigint        NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认，非 0 时关联 saas_tenant.id',

    `active_template_code` varchar(64) GENERATED ALWAYS AS (
                               IF(`deleted_at` IS NULL, `template_code`, NULL)
                               ) STORED COMMENT '有效模板编码，用于软删除下的唯一约束',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_messaging_sms_template_tenant_code_active` (`tenant_id`, `active_template_code`),
    KEY `idx_messaging_sms_template_channel_id` (`channel_id`),
    KEY `idx_messaging_sms_template_template_type` (`template_type`),
    KEY `idx_messaging_sms_template_tenant_status` (`tenant_id`, `status`),
    KEY `idx_messaging_sms_template_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息域-短信模板表';

-- ============================================================
-- 消息域-短信发送日志表；日志表；只增不改、数据量大；按租户隔离；
-- 顶级企业标准：只保留创建时间，不搞逻辑删除、不搞乐观锁；建议按月分区或归档
-- ============================================================
DROP TABLE IF EXISTS `messaging_sms_log`;
CREATE TABLE `messaging_sms_log`
(
    `id`                       bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `channel_id`               bigint        NOT NULL COMMENT '短信渠道ID，关联 messaging_sms_channel.id',
    `channel_code`             varchar(64)   NOT NULL COMMENT '短信渠道编码（冗余）',
    `template_id`              bigint        NOT NULL COMMENT '模板ID，关联 messaging_sms_template.id',
    `template_code`            varchar(64)   NOT NULL COMMENT '模板编码（快照）',
    `template_type`            tinyint       NOT NULL COMMENT '模板类型（快照）：1-验证码；2-通知；3-营销',
    `template_content`         varchar(1024) NOT NULL COMMENT '渲染后的短信内容（快照）',
    `template_params`          json                   DEFAULT NULL COMMENT '模板参数（快照）',
    `provider_template_id`     varchar(64)   NOT NULL COMMENT '第三方平台的模板编号（快照）',
    `phone_number`             varchar(32)   NOT NULL COMMENT '手机号（E.164 格式）',
    `user_id`   bigint      NULL DEFAULT NULL COMMENT '用户ID，NULL 表示未登录或系统行为',
    `user_type` varchar(20) NULL DEFAULT NULL COMMENT '用户归属域：INTERNAL-内部员工；EXTERNAL-外部会员；NULL 表示未登录',
    `send_status`              tinyint       NOT NULL DEFAULT 0 COMMENT '发送状态：0-初始化；10-发送成功；20-发送失败；30-不发送',
    `sent_at`                  datetime(3) NULL DEFAULT NULL COMMENT '发送时间（UTC）',
    `provider_send_code`       varchar(64)            DEFAULT NULL COMMENT '服务商发送结果编码',
    `provider_send_message`    varchar(512)           DEFAULT NULL COMMENT '服务商发送结果消息',
    `provider_request_id`      varchar(128)           DEFAULT NULL COMMENT '服务商返回的请求 ID',
    `provider_serial_number`   varchar(128)           DEFAULT NULL COMMENT '服务商返回的流水号',
    `receive_status`           tinyint       NOT NULL DEFAULT 0 COMMENT '接收状态：0-等待结果；10-接收成功；20-接收失败',
    `received_at`              datetime(3) NULL DEFAULT NULL COMMENT '接收时间（UTC）',
    `provider_receive_code`    varchar(64)            DEFAULT NULL COMMENT '服务商接收结果编码',
    `provider_receive_message` varchar(512)           DEFAULT NULL COMMENT '服务商接收结果说明',
    `created_at`               datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `tenant_id`                bigint        NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认，非 0 时关联 saas_tenant.id',
    PRIMARY KEY (`id`),
    KEY `idx_messaging_sms_log_phone_created_at` (`phone_number`, `created_at`),
    KEY `idx_messaging_sms_log_user_created_at` (`user_id`, `user_type`, `created_at`),
    KEY `idx_messaging_sms_log_template_code_created_at` (`template_code`, `created_at`),
    KEY `idx_messaging_sms_log_send_status_created_at` (`send_status`, `created_at`),
    KEY `idx_messaging_sms_log_tenant_id_created_at` (`tenant_id`, `created_at`),
    KEY `idx_messaging_sms_log_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息域-短信发送日志表';

-- ============================================================
-- 消息域-短信验证码表；临时表；数据生命周期短；按租户隔离；
-- 顶级企业标准：不搞逻辑删除、不搞乐观锁；必须有 TTL 清理任务；
-- 验证码只存哈希，不存明文
-- ============================================================
DROP TABLE IF EXISTS `messaging_sms_verification_code`;
CREATE TABLE `messaging_sms_verification_code`
(
    `id`                 bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `phone_number`       varchar(32)  NOT NULL COMMENT '手机号（E.164 格式）',
    `code_hash`          varchar(128) NOT NULL COMMENT '验证码哈希（不存明文，如 bcrypt / SHA-256 + salt）',
    `scene`              tinyint      NOT NULL COMMENT '发送场景：1-登录；2-注册；3-重置密码；4-修改手机；5-绑定手机；6-其他',
    `request_ip`         varchar(45)  NOT NULL COMMENT '请求 IP（兼容 IPv6）',
    `request_user_agent` varchar(1024)         DEFAULT NULL COMMENT '请求 User-Agent',
    `is_used`            tinyint   NOT NULL DEFAULT 0 COMMENT '是否已使用：0-未使用；1-已使用',
    `used_at`            datetime(3) NULL DEFAULT NULL COMMENT '使用时间（UTC）',
    `used_ip`            varchar(45)           DEFAULT NULL COMMENT '使用 IP（兼容 IPv6）',
    `daily_sequence`     smallint  NOT NULL DEFAULT 1 COMMENT '当日发送的第几条（用于限流）',
    `expires_at`         datetime(3) NOT NULL COMMENT '过期时间（UTC）',
    `created_at`         datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `tenant_id`          bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认，非 0 时关联 saas_tenant.id',
    PRIMARY KEY (`id`),
    KEY `idx_messaging_sms_verification_code_phone_scene_created_at` (`phone_number`, `scene`, `created_at`),
    KEY `idx_messaging_sms_verification_code_expires_at` (`expires_at`),
    KEY `idx_messaging_sms_verification_code_tenant_created_at` (`tenant_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息域-短信验证码表';

-- ============================================================
-- 消息域-短信渠道配置表；业务主表；全局配置，由平台管理，无多租户；
-- 包含完整审计字段、逻辑删除、乐观锁；凭证加密存储
-- ============================================================
DROP TABLE IF EXISTS `messaging_sms_channel`;
CREATE TABLE `messaging_sms_channel`
(
    `id`                      bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `channel_code`            varchar(64)  NOT NULL COMMENT '渠道编码，全局唯一（如 ALIYUN、TENCENT、HUAWEI）',
    `channel_name`            varchar(100) NOT NULL COMMENT '渠道名称',
    `signature`               varchar(100) NOT NULL COMMENT '短信签名（发送方显示名）',
    `access_key_encrypted`    varbinary(512) NOT NULL COMMENT '加密后的 AccessKey',
    `access_secret_encrypted` varbinary(512) NOT NULL COMMENT '加密后的 AccessSecret',
    `callback_url`            varchar(1024)         DEFAULT NULL COMMENT '短信状态回调 URL',
    `status`                  tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0-启用；1-禁用',
    `description`             varchar(500)          DEFAULT NULL COMMENT '描述',
    `version`                 int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `created_by`              bigint                DEFAULT NULL COMMENT '创建人ID',
    `created_at`              datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by`              bigint                DEFAULT NULL COMMENT '更新人ID',
    `updated_at`              datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    `deleted_at`              datetime(3) NULL DEFAULT NULL COMMENT '删除时间（UTC），NULL表示未删除',

    `active_channel_code`     varchar(64) GENERATED ALWAYS AS (
                                  IF(`deleted_at` IS NULL, `channel_code`, NULL)
                                  ) STORED COMMENT '有效渠道编码，用于软删除下的唯一约束',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_messaging_sms_channel_code_active` (`active_channel_code`),
    KEY `idx_messaging_sms_channel_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息域-短信渠道配置表';

-- ============================================================
-- 消息域-站内信模板表；业务主表；按租户隔离；包含完整审计字段、逻辑删除、乐观锁、多租户
-- ============================================================
DROP TABLE IF EXISTS `messaging_notification_template`;
CREATE TABLE `messaging_notification_template`
(
    `id`                   bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `template_code`        varchar(64)   NOT NULL COMMENT '模板编码，租户内唯一',
    `template_name`        varchar(100)  NOT NULL COMMENT '模板名称',
    `template_type`        tinyint       NOT NULL COMMENT '模板类型：1-通知公告；2-系统消息',
    `sender_name`          varchar(100)  NOT NULL COMMENT '发送人名称（展示用，如“系统消息”）',
    `content`              varchar(1024) NOT NULL COMMENT '模板内容，使用 {key} 占位',
    `params`               json                   DEFAULT NULL COMMENT '模板参数数组',
    `status`               tinyint       NOT NULL DEFAULT 0 COMMENT '状态：0-启用；1-禁用',
    `description`          varchar(500)           DEFAULT NULL COMMENT '描述',
    `version`              int           NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `created_by`           bigint                 DEFAULT NULL COMMENT '创建人ID',
    `created_at`           datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by`           bigint                 DEFAULT NULL COMMENT '更新人ID',
    `updated_at`           datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    `deleted_at`           datetime(3) NULL DEFAULT NULL COMMENT '删除时间（UTC），NULL表示未删除',
    `tenant_id`            bigint        NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认，非 0 时关联 saas_tenant.id',

    `active_template_code` varchar(64) GENERATED ALWAYS AS (
                               IF(`deleted_at` IS NULL, `template_code`, NULL)
                               ) STORED COMMENT '有效模板编码，用于软删除下的唯一约束',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_messaging_notification_template_tenant_code_active` (`tenant_id`, `active_template_code`),
    KEY `idx_messaging_notification_template_template_type` (`template_type`),
    KEY `idx_messaging_notification_template_tenant_status` (`tenant_id`, `status`),
    KEY `idx_messaging_notification_template_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息域-站内信模板表';

-- ============================================================
-- 消息域-站内信消息表；业务主表；按租户隔离；用户可操作（标记已读）；
-- 包含创建/更新时间、逻辑删除、多租户；不搞乐观锁（读状态更新无需版本号）
-- ============================================================
DROP TABLE IF EXISTS `messaging_notification`;
CREATE TABLE `messaging_notification`
(
    `id`                   bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`              bigint        NOT NULL COMMENT '接收用户ID',
    `user_type` varchar(20) NOT NULL COMMENT '用户归属域：INTERNAL-内部员工；EXTERNAL-外部会员（物理路由键）',
    `template_id`          bigint        NOT NULL COMMENT '模板ID，关联 messaging_notification_template.id',
    `template_code`        varchar(64)   NOT NULL COMMENT '模板编码（快照）',
    `template_type`        tinyint       NOT NULL COMMENT '模板类型（快照）：1-通知公告；2-系统消息',
    `template_sender_name` varchar(100)  NOT NULL COMMENT '模板发送人名称（快照）',
    `template_content`     varchar(1024) NOT NULL COMMENT '渲染后的消息内容（快照）',
    `template_params`      json                   DEFAULT NULL COMMENT '模板参数（快照）',
    `is_read`              tinyint    NOT NULL DEFAULT 0 COMMENT '是否已读：0-未读；1-已读',
    `read_at`              datetime(3) NULL DEFAULT NULL COMMENT '阅读时间（UTC）',
    `created_at`           datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_at`           datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    `deleted_at`           datetime(3) NULL DEFAULT NULL COMMENT '删除时间（UTC），NULL表示未删除',
    `tenant_id`            bigint        NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认，非 0 时关联 saas_tenant.id',
    PRIMARY KEY (`id`),
    KEY `idx_messaging_notification_user_unread` (`user_id`, `user_type`, `is_read`, `created_at`),
    KEY `idx_messaging_notification_user_created_at` (`user_id`, `user_type`, `created_at`),
    KEY `idx_messaging_notification_tenant_id` (`tenant_id`),
    KEY `idx_messaging_notification_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息域-站内信消息表';

-- ============================================================
-- 消息域-通知公告表；业务主表；按租户隔离；包含完整审计字段、逻辑删除、乐观锁、多租户
-- ============================================================
DROP TABLE IF EXISTS `messaging_notice`;
CREATE TABLE `messaging_notice`
(
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '公告ID',
    `title`       varchar(255) NOT NULL COMMENT '公告标题',
    `content`     mediumtext   NOT NULL COMMENT '公告内容（HTML 富文本）',
    `notice_type` tinyint      NOT NULL COMMENT '公告类型：1-通知；2-公告',
    `status`      tinyint      NOT NULL DEFAULT 0 COMMENT '公告状态：0-正常；1-关闭',
    `version`     int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `created_by`  bigint                DEFAULT NULL COMMENT '创建人ID',
    `created_at`  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by`  bigint                DEFAULT NULL COMMENT '更新人ID',
    `updated_at`  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    `deleted_at`  datetime(3) NULL DEFAULT NULL COMMENT '删除时间（UTC），NULL表示未删除',
    `tenant_id`   bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认，非 0 时关联 saas_tenant.id',
    PRIMARY KEY (`id`),
    KEY `idx_messaging_notice_notice_type` (`notice_type`),
    KEY `idx_messaging_notice_status_created_at` (`status`, `created_at`),
    KEY `idx_messaging_notice_tenant_id` (`tenant_id`),
    KEY `idx_messaging_notice_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息域-通知公告表';

-- ============================================================
-- 消息域-邮件模板表；业务主表；按租户隔离；包含完整审计字段、逻辑删除、乐观锁、多租户
-- ============================================================
DROP TABLE IF EXISTS `messaging_mail_template`;
CREATE TABLE `messaging_mail_template`
(
    `id`                   bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `template_code`        varchar(64)  NOT NULL COMMENT '模板编码，租户内唯一',
    `template_name`        varchar(100) NOT NULL COMMENT '模板名称',
    `mail_account_id`      bigint       NOT NULL COMMENT '发送邮箱账号ID，关联 messaging_mail_account.id',
    `sender_name`          varchar(100)          DEFAULT NULL COMMENT '发送人名称（展示用）',
    `subject`              varchar(255) NOT NULL COMMENT '邮件主题，使用 {key} 占位',
    `content`              mediumtext   NOT NULL COMMENT '邮件内容（HTML 富文本），使用 {key} 占位',
    `params`               json                  DEFAULT NULL COMMENT '模板参数数组',
    `status`               tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0-启用；1-禁用',
    `description`          varchar(500)          DEFAULT NULL COMMENT '描述',
    `version`              int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `created_by`           bigint                DEFAULT NULL COMMENT '创建人ID',
    `created_at`           datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by`           bigint                DEFAULT NULL COMMENT '更新人ID',
    `updated_at`           datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    `deleted_at`           datetime(3) NULL DEFAULT NULL COMMENT '删除时间（UTC），NULL表示未删除',
    `tenant_id`            bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认，非 0 时关联 saas_tenant.id',

    `active_template_code` varchar(64) GENERATED ALWAYS AS (
                               IF(`deleted_at` IS NULL, `template_code`, NULL)
                               ) STORED COMMENT '有效模板编码，用于软删除下的唯一约束',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_messaging_mail_template_tenant_code_active` (`tenant_id`, `active_template_code`),
    KEY `idx_messaging_mail_template_account_id` (`mail_account_id`),
    KEY `idx_messaging_mail_template_tenant_status` (`tenant_id`, `status`),
    KEY `idx_messaging_mail_template_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息域-邮件模板表';

-- ============================================================
-- 消息域-邮件发送日志表；日志表；只增不改、数据量大；按租户隔离；
-- 顶级企业标准：只保留创建时间，不搞逻辑删除、不搞乐观锁；建议按月分区或归档
-- ============================================================
DROP TABLE IF EXISTS `messaging_mail_log`;
CREATE TABLE `messaging_mail_log`
(
    `id`                   bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`   bigint      NULL DEFAULT NULL COMMENT '用户ID，NULL 表示未登录或系统行为',
    `user_type` varchar(20) NULL DEFAULT NULL COMMENT '用户归属域：INTERNAL-内部员工；EXTERNAL-外部会员；NULL 表示未登录',
    `to_recipients`        varchar(2048) NOT NULL COMMENT '收件人邮箱地址（多个用逗号分隔）',
    `cc_recipients`        varchar(2048)          DEFAULT NULL COMMENT '抄送邮箱地址（多个用逗号分隔）',
    `bcc_recipients`       varchar(2048)          DEFAULT NULL COMMENT '密送邮箱地址（多个用逗号分隔）',
    `mail_account_id`      bigint        NOT NULL COMMENT '邮箱账号ID，关联 messaging_mail_account.id',
    `from_email`           varchar(254)  NOT NULL COMMENT '发件人邮箱地址',
    `template_id`          bigint        NOT NULL COMMENT '模板ID，关联 messaging_mail_template.id',
    `template_code`        varchar(64)   NOT NULL COMMENT '模板编码（快照）',
    `template_sender_name` varchar(100)           DEFAULT NULL COMMENT '模板发送人名称（快照）',
    `template_subject`     varchar(255)  NOT NULL COMMENT '邮件主题（快照）',
    `template_content`     mediumtext    NOT NULL COMMENT '邮件内容（快照）',
    `template_params`      json                   DEFAULT NULL COMMENT '模板参数（快照）',
    `send_status`          tinyint       NOT NULL DEFAULT 0 COMMENT '发送状态：0-初始化；10-发送成功；20-发送失败；30-不发送',
    `sent_at`              datetime(3) NULL DEFAULT NULL COMMENT '发送时间（UTC）',
    `provider_message_id`  varchar(255)           DEFAULT NULL COMMENT '邮件服务商返回的消息ID',
    `error_message`        text                   DEFAULT NULL COMMENT '发送失败时的异常信息',
    `created_at`           datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `tenant_id`            bigint        NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认，非 0 时关联 saas_tenant.id',
    PRIMARY KEY (`id`),
    KEY `idx_messaging_mail_log_user_created_at` (`user_id`, `user_type`, `created_at`),
    KEY `idx_messaging_mail_log_to_recipients_created_at` (`to_recipients`(255), `created_at`),
    KEY `idx_messaging_mail_log_send_status_created_at` (`send_status`, `created_at`),
    KEY `idx_messaging_mail_log_tenant_id_created_at` (`tenant_id`, `created_at`),
    KEY `idx_messaging_mail_log_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息域-邮件发送日志表';

-- ============================================================
-- 消息域-邮箱账号配置表；业务主表；按租户隔离；包含完整审计字段、逻辑删除、乐观锁、多租户；
-- SMTP 密码加密存储
-- ============================================================
DROP TABLE IF EXISTS `messaging_mail_account`;
CREATE TABLE `messaging_mail_account`
(
    `id`                      bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `account_name`            varchar(100) NOT NULL COMMENT '账号名称（展示用，如“通知邮箱”）',
    `from_email`              varchar(254) NOT NULL COMMENT '发件人邮箱地址',
    `sender_name`             varchar(100)          DEFAULT NULL COMMENT '发件人显示名称',
    `smtp_host`               varchar(255) NOT NULL COMMENT 'SMTP 服务器域名',
    `smtp_port`               int          NOT NULL COMMENT 'SMTP 服务器端口：25、465、587、2525 等',
    `smtp_username`           varchar(255) NOT NULL COMMENT 'SMTP 登录用户名',
    `smtp_password_encrypted` varbinary(512) NOT NULL COMMENT '加密后的 SMTP 登录密码或授权码',
    `is_ssl_enabled`          tinyint   NOT NULL DEFAULT 0 COMMENT '是否启用 SSL：0-否；1-是（通常端口 465）',
    `is_starttls_enabled`     tinyint   NOT NULL DEFAULT 0 COMMENT '是否启用 STARTTLS：0-否；1-是（通常端口 587）',
    `status`                  tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0-启用；1-禁用',
    `description`             varchar(500)          DEFAULT NULL COMMENT '描述',
    `version`                 int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `created_by`              bigint                DEFAULT NULL COMMENT '创建人ID',
    `created_at`              datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by`              bigint                DEFAULT NULL COMMENT '更新人ID',
    `updated_at`              datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    `deleted_at`              datetime(3) NULL DEFAULT NULL COMMENT '删除时间（UTC），NULL表示未删除',
    `tenant_id`               bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认，非 0 时关联 saas_tenant.id',

    `active_from_email`       varchar(254) GENERATED ALWAYS AS (
                                  IF(`deleted_at` IS NULL, `from_email`, NULL)
                                  ) STORED COMMENT '有效发件人邮箱，用于软删除下的唯一约束',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_messaging_mail_account_tenant_from_email_active` (`tenant_id`, `active_from_email`),
    KEY `idx_messaging_mail_account_tenant_status` (`tenant_id`, `status`),
    KEY `idx_messaging_mail_account_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息域-邮箱账号配置表';


-- ============================================================
-- OAuth2 域-客户端配置表；业务主表；按租户隔离；包含完整审计字段、逻辑删除、乐观锁、多租户；
-- 客户端密钥加密存储
-- ============================================================
DROP TABLE IF EXISTS `oauth2_client`;
CREATE TABLE `oauth2_client`
(
    `id`                        bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `client_id`                 varchar(128) NOT NULL COMMENT '客户端编号，租户内唯一',
    `client_secret_encrypted`   varbinary(512) NOT NULL COMMENT '加密后的客户端密钥',
    `client_name`               varchar(128) NOT NULL COMMENT '应用名',
    `logo_url`                  varchar(1024)         DEFAULT NULL COMMENT '应用图标 URL',
    `description`               varchar(512)          DEFAULT NULL COMMENT '应用描述',
    `status`                    tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0-启用；1-禁用',
    `access_token_ttl_seconds`  int          NOT NULL COMMENT '访问令牌有效期（秒）',
    `refresh_token_ttl_seconds` int          NOT NULL COMMENT '刷新令牌有效期（秒）',
    `redirect_uris`             json         NOT NULL COMMENT '可重定向的 URI 地址数组',
    `authorized_grant_types`    json         NOT NULL COMMENT '授权类型数组：password、authorization_code、implicit、refresh_token、client_credentials',
    `scopes`                    json                  DEFAULT NULL COMMENT '授权范围数组',
    `auto_approve_scopes`       json                  DEFAULT NULL COMMENT '自动通过的授权范围数组',
    `authorities`               json                  DEFAULT NULL COMMENT '权限数组',
    `resource_ids`              json                  DEFAULT NULL COMMENT '资源ID数组',
    `additional_information`    json                  DEFAULT NULL COMMENT '附加信息',
    `version`                   int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `created_by`                bigint                DEFAULT NULL COMMENT '创建人ID',
    `created_at`                datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by`                bigint                DEFAULT NULL COMMENT '更新人ID',
    `updated_at`                datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    `deleted_at`                datetime(3) NULL DEFAULT NULL COMMENT '删除时间（UTC），NULL表示未删除',
    `tenant_id`                 bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认，非 0 时关联 saas_tenant.id',

    `active_client_id`          varchar(128) GENERATED ALWAYS AS (
                                    IF(`deleted_at` IS NULL, `client_id`, NULL)
                                    ) STORED COMMENT '有效客户端编号，用于软删除下的唯一约束',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_oauth2_client_tenant_client_id_active` (`tenant_id`, `active_client_id`),
    KEY `idx_oauth2_client_status` (`status`),
    KEY `idx_oauth2_client_tenant_id` (`tenant_id`),
    KEY `idx_oauth2_client_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OAuth2 域-客户端配置表';

-- ============================================================
-- OAuth2 域-授权码表；临时表；数据生命周期短；按租户隔离；
-- 顶级企业标准：不搞逻辑删除、不搞乐观锁；授权码只存哈希，不存明文；必须有 TTL 清理任务
-- ============================================================
DROP TABLE IF EXISTS `oauth2_authorization_code`;
CREATE TABLE `oauth2_authorization_code`
(
    `id`                       bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `authorization_code_hash`  varchar(128) NOT NULL COMMENT '授权码哈希（SHA-256，不存明文，一次性使用）',
    `client_id`                varchar(128) NOT NULL COMMENT '客户端编号',
    `user_id`                  bigint       NOT NULL COMMENT '用户ID',
    `user_type` varchar(20) NOT NULL COMMENT '用户归属域：INTERNAL-内部员工；EXTERNAL-外部会员（物理路由键）',
    `scopes`                   json                  DEFAULT NULL COMMENT '授权范围数组',
    `redirect_uri`             varchar(1024)         DEFAULT NULL COMMENT '重定向 URI',
    `state`                    varchar(255)          DEFAULT NULL COMMENT '客户端状态参数，原样返回；NULL 表示客户端未提供',
    `is_used`                  tinyint   NOT NULL DEFAULT 0 COMMENT '是否已使用：0-未使用；1-已使用',
    `used_at`                  datetime(3) NULL DEFAULT NULL COMMENT '使用时间（UTC）',
    `expires_at`               datetime(3) NOT NULL COMMENT '过期时间（UTC）',
    `created_at`               datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `tenant_id`                bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认，非 0 时关联 saas_tenant.id',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_oauth2_authorization_code_hash_tenant` (`tenant_id`, `authorization_code_hash`),
    KEY `idx_oauth2_authorization_code_client_id` (`client_id`),
    KEY `idx_oauth2_authorization_code_user` (`user_id`, `user_type`),
    KEY `idx_oauth2_authorization_code_expires_at` (`expires_at`),
    KEY `idx_oauth2_authorization_code_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OAuth2 域-授权码表';

-- ============================================================
-- OAuth2 域-刷新令牌表；临时表；数据生命周期短；按租户隔离；
-- 顶级企业标准：不搞逻辑删除、不搞乐观锁；刷新令牌只存哈希，不存明文；必须有 TTL 清理任务
-- ============================================================
DROP TABLE IF EXISTS `oauth2_refresh_token`;
CREATE TABLE `oauth2_refresh_token`
(
    `id`                 bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `refresh_token_hash` varchar(128) NOT NULL COMMENT '刷新令牌哈希（SHA-256，不存明文）',
    `access_token_hash`  varchar(128)          DEFAULT NULL COMMENT '关联的访问令牌哈希（SHA-256）',
    `client_id`          varchar(128) NOT NULL COMMENT '客户端编号',
    `user_id`            bigint       NOT NULL COMMENT '用户ID',
    `user_type` varchar(20) NOT NULL COMMENT '用户归属域：INTERNAL-内部员工；EXTERNAL-外部会员（物理路由键）',
    `scopes`             json                  DEFAULT NULL COMMENT '授权范围数组',
    `is_revoked`         tinyint   NOT NULL DEFAULT 0 COMMENT '是否已撤销：0-否；1-是',
    `revoked_at`         datetime(3) NULL DEFAULT NULL COMMENT '撤销时间（UTC）',
    `expires_at`         datetime(3) NOT NULL COMMENT '过期时间（UTC）',
    `created_at`         datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `tenant_id`          bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认，非 0 时关联 saas_tenant.id',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_oauth2_refresh_token_hash_tenant` (`tenant_id`, `refresh_token_hash`),
    KEY `idx_oauth2_refresh_token_access_token_hash` (`access_token_hash`),
    KEY `idx_oauth2_refresh_token_client_id` (`client_id`),
    KEY `idx_oauth2_refresh_token_user` (`user_id`, `user_type`),
    KEY `idx_oauth2_refresh_token_expires_at` (`expires_at`),
    KEY `idx_oauth2_refresh_token_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OAuth2 域-刷新令牌表';

-- ============================================================
-- OAuth2 域-访问令牌表；临时表；数据生命周期短；按租户隔离；
-- 顶级企业标准：不搞逻辑删除、不搞乐观锁；访问令牌只存哈希，不存明文；必须有 TTL 清理任务
-- ============================================================
DROP TABLE IF EXISTS `oauth2_access_token`;
CREATE TABLE `oauth2_access_token`
(
    `id`                 bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `access_token_hash`  varchar(128) NOT NULL COMMENT '访问令牌哈希（SHA-256，不存明文）',
    `refresh_token_hash` varchar(128)          DEFAULT NULL COMMENT '刷新令牌哈希（SHA-256）',
    `client_id`          varchar(128) NOT NULL COMMENT '客户端编号',
    `user_id`            bigint       NOT NULL COMMENT '用户ID',
    `user_type` varchar(20) NOT NULL COMMENT '用户归属域：INTERNAL-内部员工；EXTERNAL-外部会员（物理路由键）',
    `user_info`          json                  DEFAULT NULL COMMENT '用户信息快照',
    `scopes`             json                  DEFAULT NULL COMMENT '授权范围数组',
    `is_revoked`         tinyint   NOT NULL DEFAULT 0 COMMENT '是否已撤销：0-否；1-是',
    `revoked_at`         datetime(3) NULL DEFAULT NULL COMMENT '撤销时间（UTC）',
    `expires_at`         datetime(3) NOT NULL COMMENT '过期时间（UTC）',
    `created_at`         datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `tenant_id`          bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认，非 0 时关联 saas_tenant.id',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_oauth2_access_token_hash` (`access_token_hash`),
    KEY `idx_oauth2_access_token_refresh_token_hash` (`refresh_token_hash`),
    KEY `idx_oauth2_access_token_client_id` (`client_id`),
    KEY `idx_oauth2_access_token_user` (`user_id`, `user_type`),
    KEY `idx_oauth2_access_token_expires_at` (`expires_at`),
    KEY `idx_oauth2_access_token_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OAuth2 域-访问令牌表';

-- ============================================================
-- OAuth2 域-授权同意表；业务主表；按租户隔离；用户已批准的授权范围记录；
-- 包含完整审计字段、逻辑删除、乐观锁、多租户
-- ============================================================
DROP TABLE IF EXISTS `oauth2_authorization_consent`;
CREATE TABLE `oauth2_authorization_consent`
(
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `client_id`   varchar(128) NOT NULL COMMENT '客户端编号',
    `user_id`     bigint       NOT NULL COMMENT '用户ID',
    `user_type` varchar(20) NOT NULL COMMENT '用户归属域：INTERNAL-内部员工；EXTERNAL-外部会员（物理路由键）',
    `scopes`      json         NOT NULL COMMENT '已批准的授权范围数组',
    `is_approved` tinyint   NOT NULL DEFAULT 0 COMMENT '是否已批准：0-未批准；1-已批准',
    `expires_at`  datetime(3) NOT NULL COMMENT '过期时间（UTC）',
    `version`     int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `created_by`  bigint                DEFAULT NULL COMMENT '创建人ID',
    `created_at`  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by`  bigint                DEFAULT NULL COMMENT '更新人ID',
    `updated_at`  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    `deleted_at`  datetime(3) NULL DEFAULT NULL COMMENT '删除时间（UTC），NULL表示未删除',
    `tenant_id`   bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认，非 0 时关联 saas_tenant.id',

    `active_consent_key` varchar(255) GENERATED ALWAYS AS (
                      IF(`deleted_at` IS NULL, CONCAT(`client_id`, ':', `user_id`, ':', `user_type`), NULL)
                      ) STORED COMMENT '有效授权同意键，用于软删除下的唯一约束',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_oauth2_authorization_consent_tenant_key_active` (`tenant_id`, `active_consent_key`),
    KEY `idx_oauth2_authorization_consent_client_id` (`client_id`),
    KEY `idx_oauth2_authorization_consent_user` (`user_id`, `user_type`),
    KEY `idx_oauth2_authorization_consent_expires_at` (`expires_at`),
    KEY `idx_oauth2_authorization_consent_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OAuth2 域-授权同意表';

-- ============================================================
-- 审计域-操作审计日志表；日志表；只增不改、数据量大；按租户隔离；
-- 顶级企业标准：只保留创建时间，不搞逻辑删除、不搞乐观锁；建议按月分区或归档；
-- 记录操作行为用于合规审计，敏感字段必须脱敏
-- ============================================================
DROP TABLE IF EXISTS `audit_operation_log`;
CREATE TABLE `audit_operation_log`
(
    `id`               bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `trace_id`         varchar(64)            DEFAULT NULL COMMENT '链路追踪ID，NULL 表示未采集',
    `user_id`   bigint      NULL DEFAULT NULL COMMENT '用户ID，NULL 表示未登录或系统行为',
    `user_type` varchar(20) NULL DEFAULT NULL COMMENT '用户归属域：INTERNAL-内部员工；EXTERNAL-外部会员；NULL 表示未登录',
    `username`         varchar(64)            DEFAULT NULL COMMENT '操作账号（快照），NULL 表示系统/未登录',
    `ip_address`       varchar(45)            DEFAULT NULL COMMENT '操作来源 IP（兼容 IPv6）',
    `user_agent`       varchar(1024)          DEFAULT NULL COMMENT '客户端 User-Agent',
    `http_method`      varchar(16)            DEFAULT NULL COMMENT 'HTTP 方法：GET、POST、PUT、DELETE 等；NULL 表示非 HTTP 操作',
    `request_url`      varchar(2048)          DEFAULT NULL COMMENT '请求 URL；NULL 表示非 HTTP 操作',
    `operation_module` varchar(64)   NOT NULL COMMENT '操作模块（如：订单管理、用户管理）',
    `operation_name`   varchar(64)   NOT NULL COMMENT '操作名称（如：创建订单、删除用户）',
    `operation_type`   tinyint       NOT NULL DEFAULT 0 COMMENT '操作类型：0-其它；1-查询；2-新增；3-修改；4-删除；5-导出；6-导入',
    `business_id`      bigint        NOT NULL DEFAULT 0 COMMENT '业务数据ID（如订单ID、用户ID）',
    `action`           text          NOT NULL COMMENT '操作内容描述（需脱敏）',
    `is_success`       tinyint    NOT NULL DEFAULT 1 COMMENT '操作结果：0-失败；1-成功',
    `error_message`    varchar(2048)          DEFAULT NULL COMMENT '失败时的异常信息（需脱敏）',
    `extra_data`       mediumtext             DEFAULT NULL COMMENT '扩展数据（JSON 格式，记录变更前后值，需脱敏）',
    `created_at`       datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `tenant_id`        bigint        NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认，非 0 时关联 saas_tenant.id',
    PRIMARY KEY (`id`),
    KEY `idx_audit_operation_log_trace_id` (`trace_id`),
    KEY `idx_audit_operation_log_user_created_at` (`user_id`, `user_type`, `created_at`),
    KEY `idx_audit_operation_log_module_name_created_at` (`operation_module`, `operation_name`, `created_at`),
    KEY `idx_audit_operation_log_business_id_created_at` (`business_id`, `created_at`),
    KEY `idx_audit_operation_log_tenant_id_created_at` (`tenant_id`, `created_at`),
    KEY `idx_audit_operation_log_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计域-操作审计日志表';

-- ============================================================
-- 审计域-登录审计日志表；日志表；只增不改、数据量大；按租户隔离；
-- 顶级企业标准：只保留创建时间，不搞逻辑删除、不搞乐观锁；建议按月分区或归档；
-- 记录登录行为用于安全审计，IP、UA 等字段需注意合规
-- ============================================================
DROP TABLE IF EXISTS `audit_login_log`;
CREATE TABLE `audit_login_log`
(
    `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `trace_id`      varchar(64)           DEFAULT NULL COMMENT '链路追踪ID（用于关联同一请求的所有日志），NULL 表示未采集',
    `user_id`   bigint      NULL DEFAULT NULL COMMENT '用户ID，NULL 表示未登录或系统行为',
    `user_type` varchar(20) NULL DEFAULT NULL COMMENT '用户归属域：INTERNAL-内部员工；EXTERNAL-外部会员；NULL 表示未登录',
    `username`      varchar(64)           DEFAULT NULL COMMENT '登录账号（快照，登录失败时也可能是输入值），NULL 表示未提供/未采集',
    `login_type`    tinyint      NOT NULL COMMENT '登录类型：100-账号密码登录；101-社交登录；102-短信登录；103-邮箱登录；104-一键登录',
    `login_result`  tinyint      NOT NULL COMMENT '登录结果：0-成功；10-账号或密码不正确；20-用户被禁用；30-验证码不存在；31-验证码不正确；100-未知异常',
    `login_message` varchar(512)          DEFAULT NULL COMMENT '登录结果说明（便于排障，需脱敏）',
    `ip_address`    varchar(45)  NOT NULL COMMENT '登录来源 IP（兼容 IPv6）',
    `ip_region`     varchar(128)          DEFAULT NULL COMMENT 'IP 归属地（如：中国-陕西-西安）',
    `user_agent`    varchar(1024) NOT NULL COMMENT '客户端 User-Agent',
    `device_type`   varchar(32)           DEFAULT NULL COMMENT '设备类型：PC、iOS、Android、小程序、H5 等',
    `os`            varchar(64)           DEFAULT NULL COMMENT '操作系统（如：Windows 10、macOS、iOS 17）',
    `browser`       varchar(64)           DEFAULT NULL COMMENT '浏览器（如：Chrome、Safari、Edge）',
    `created_at`    datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `tenant_id`     bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认，非 0 时关联 saas_tenant.id',
    PRIMARY KEY (`id`),
    KEY `idx_audit_login_log_trace_id` (`trace_id`),
    KEY `idx_audit_login_log_username_created_at` (`username`, `created_at`),
    KEY `idx_audit_login_log_user_created_at` (`user_id`, `user_type`, `created_at`),
    KEY `idx_audit_login_log_login_result_created_at` (`login_result`, `created_at`),
    KEY `idx_audit_login_log_tenant_id_created_at` (`tenant_id`, `created_at`),
    KEY `idx_audit_login_log_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计域-登录审计日志表';


-- ============================================================
-- 元数据域-字典类型表；业务主表；支持全局默认（tenant_id=0）与租户自定义；
-- 包含完整审计字段、逻辑删除、乐观锁、多租户
-- ============================================================
DROP TABLE IF EXISTS `metadata_dictionary_type`;
CREATE TABLE `metadata_dictionary_type`
(
    `id`                     bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `dictionary_type_name`   varchar(100) NOT NULL COMMENT '字典名称',
    `dictionary_type_code`   varchar(100) NOT NULL COMMENT '字典编码（如 system_user_sex、common_status）',
    `status`                 tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0-正常；1-停用',
    `description`            varchar(500)          DEFAULT NULL COMMENT '描述',
    `version`                int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `created_by`             bigint                DEFAULT NULL COMMENT '创建人ID',
    `created_at`             datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by`             bigint                DEFAULT NULL COMMENT '更新人ID',
    `updated_at`             datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    `deleted_at`             datetime(3) NULL DEFAULT NULL COMMENT '删除时间（UTC），NULL表示未删除',
    `tenant_id`              bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认，非 0 时关联 saas_tenant.id',

    `active_type_code`       varchar(100) GENERATED ALWAYS AS (
                                 IF(`deleted_at` IS NULL, `dictionary_type_code`, NULL)
                                 ) STORED COMMENT '有效字典编码，用于软删除下的唯一约束',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_metadata_dictionary_type_tenant_code_active` (`tenant_id`, `active_type_code`),
    KEY `idx_metadata_dictionary_type_tenant_status` (`tenant_id`, `status`),
    KEY `idx_metadata_dictionary_type_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='元数据域-字典类型表';

-- ============================================================
-- 元数据域-字典数据表；业务主表；支持全局默认（tenant_id=0）与租户自定义；
-- 包含完整审计字段、逻辑删除、乐观锁、多租户；
-- 通过 dictionary_type_code 关联字典类型，避免跨表 ID 强耦合
-- ============================================================
DROP TABLE IF EXISTS `metadata_dictionary_data`;
CREATE TABLE `metadata_dictionary_data`
(
    `id`                     bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `dictionary_type_code`   varchar(100) NOT NULL COMMENT '字典编码，关联 metadata_dictionary_type.dictionary_type_code',
    `label`                  varchar(200) NOT NULL COMMENT '字典标签（展示用，支持多语言）',
    `value`                  varchar(200) NOT NULL COMMENT '字典键值（存储用）',
    `sort_order`             int          NOT NULL DEFAULT 0 COMMENT '排序，数字越小越靠前',
    `status`                 tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0-正常；1-停用',
    `color_type`             varchar(32)           DEFAULT NULL COMMENT '颜色类型：primary、success、info、warning、danger；NULL 表示无颜色',
    `css_class`              varchar(128)          DEFAULT NULL COMMENT 'CSS 样式类名',
    `is_default`             tinyint   NOT NULL DEFAULT 0 COMMENT '是否为默认选中项：0-否；1-是',
    `description`            varchar(500)          DEFAULT NULL COMMENT '描述',
    `version`                int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `created_by`             bigint                DEFAULT NULL COMMENT '创建人ID',
    `created_at`             datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by`             bigint                DEFAULT NULL COMMENT '更新人ID',
    `updated_at`             datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    `deleted_at`             datetime(3) NULL DEFAULT NULL COMMENT '删除时间（UTC），NULL表示未删除',
    `tenant_id`              bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认，非 0 时关联 saas_tenant.id',

    `active_value`           varchar(200) GENERATED ALWAYS AS (
                                 IF(`deleted_at` IS NULL AND `value` <> '', `value`, NULL)
                                 ) STORED COMMENT '有效字典键值，用于软删除下的唯一约束',
    `default_marker`         tinyint GENERATED ALWAYS AS (
        IF(`is_default` = 1 AND `deleted_at` IS NULL, 1, NULL)
        ) STORED COMMENT '默认项标记（同一字典下只能有一个默认项）',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_metadata_dictionary_data_tenant_type_value_active` (`tenant_id`, `dictionary_type_code`, `active_value`),
    UNIQUE KEY `uk_metadata_dictionary_data_tenant_type_default` (`tenant_id`, `dictionary_type_code`, `default_marker`),
    KEY `idx_metadata_dictionary_data_type_sort` (`dictionary_type_code`, `sort_order`),
    KEY `idx_metadata_dictionary_data_tenant_status` (`tenant_id`, `status`),
    KEY `idx_metadata_dictionary_data_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='元数据域-字典数据表';


-- ============================================================
-- 社交域-系统用户与社交账号绑定表；多对多关联表；
-- 顶级企业标准：精简审计字段（仅 created_by/created_at），不搞逻辑删除，不搞乐观锁，物理删除；复合主键；
-- 按租户隔离
-- ============================================================
DROP TABLE IF EXISTS `social_user_bind`;
CREATE TABLE `social_user_bind`
(
    `user_id`           bigint       NOT NULL COMMENT '系统用户ID',
    `user_type` varchar(20) NOT NULL COMMENT '用户归属域：INTERNAL-内部员工；EXTERNAL-外部会员（物理路由键）',
    `social_type`       tinyint      NOT NULL COMMENT '社交平台类型：1-微信；2-钉钉；3-支付宝；4-企业微信；5-飞书；6-GitHub；7-Google',
    `social_account_id` bigint       NOT NULL COMMENT '社交账号ID，关联 social_user.id',
    `tenant_id`         bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认，非 0 时关联 saas_tenant.id',
    `created_by`        bigint                DEFAULT NULL COMMENT '创建人ID',
    `created_at`        datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    PRIMARY KEY (`tenant_id`, `user_id`, `user_type`, `social_type`),
    UNIQUE KEY `uk_social_user_bind_tenant_social_account` (`tenant_id`, `social_type`, `social_account_id`),
    KEY `idx_social_user_bind_user_id` (`user_id`),
    KEY `idx_social_user_bind_social_account_id` (`social_account_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='社交域-系统用户与社交账号绑定表';

-- ============================================================
-- 社交域-社交平台用户表；业务主表；存储第三方平台用户信息；
-- 按租户隔离；包含完整审计字段；不搞逻辑删除（社交身份是唯一标识，删除会破坏引用）；
-- 注意：open_id 租户内唯一；union_id 租户内唯一但可为 NULL
-- ============================================================
DROP TABLE IF EXISTS `social_user`;
CREATE TABLE `social_user`
(
    `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `social_type`   tinyint      NOT NULL COMMENT '社交平台类型：1-微信；2-钉钉；3-支付宝；4-企业微信；5-飞书；6-GitHub；7-Google',
    `open_id`       varchar(128) NOT NULL COMMENT '社交平台用户唯一标识 openid，租户内唯一',
    `union_id`      varchar(128)          DEFAULT NULL COMMENT '社交平台 UnionID（微信等平台多应用统一标识），租户内唯一，可为 NULL',
    `display_name`  varchar(100)          DEFAULT NULL COMMENT '社交用户昵称',
    `avatar_url`    varchar(1024)         DEFAULT NULL COMMENT '社交用户头像',
    `raw_user_info` json                  DEFAULT NULL COMMENT '原始用户信息（快照）',
    `tenant_id`     bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认，非 0 时关联 saas_tenant.id',
    `created_by`    bigint                DEFAULT NULL COMMENT '创建人ID',
    `created_at`    datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by`    bigint                DEFAULT NULL COMMENT '更新人ID',
    `updated_at`    datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_social_user_tenant_type_open_id` (`tenant_id`, `social_type`, `open_id`),
    UNIQUE KEY `uk_social_user_tenant_type_union_id` (`tenant_id`, `social_type`, `union_id`),
    KEY `idx_social_user_tenant_id` (`tenant_id`),
    KEY `idx_social_user_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='社交域-社交平台用户表';

-- ============================================================
-- 社交域-社交用户令牌表；扩展表；1:1 跟随 social_user；
-- 令牌加密存储；精简审计字段（仅 created_at/updated_at）；
-- 不搞逻辑删除，不搞乐观锁；随 social_user 物理删除
-- ============================================================
DROP TABLE IF EXISTS `social_user_token`;
CREATE TABLE `social_user_token`
(
    `social_user_id`            bigint         NOT NULL COMMENT '社交用户ID，关联 social_user.id',
    `access_token_encrypted`    varbinary(2048) NOT NULL COMMENT '加密后的 access_token',
    `refresh_token_encrypted`   varbinary(2048) DEFAULT NULL COMMENT '加密后的 refresh_token',
    `access_token_expires_at`   datetime(3)            DEFAULT NULL COMMENT 'access_token 过期时间（UTC）',
    `refresh_token_expires_at`  datetime(3)            DEFAULT NULL COMMENT 'refresh_token 过期时间（UTC）',
    `created_at`                datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_at`                datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    PRIMARY KEY (`social_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='社交域-社交用户令牌表';

-- ============================================================
-- 社交域-社交客户端配置表；业务主表；按租户隔离；存储各社交平台的客户端配置；
-- 包含完整审计字段、逻辑删除、乐观锁、多租户；客户端密钥加密存储
-- ============================================================
DROP TABLE IF EXISTS `social_client`;
CREATE TABLE `social_client`
(
    `id`                      bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `client_name`             varchar(100) NOT NULL COMMENT '应用名',
    `social_type`             tinyint      NOT NULL COMMENT '社交平台类型：1-微信；2-钉钉；3-支付宝；4-企业微信；5-飞书；6-GitHub；7-Google',
    `user_type` varchar(20) NOT NULL COMMENT '用户归属域：INTERNAL-内部员工；EXTERNAL-外部会员（物理路由键）',
    `client_id`               varchar(128) NOT NULL COMMENT '客户端编号',
    `client_secret_encrypted` varbinary(512) NOT NULL COMMENT '加密后的客户端密钥',
    `agent_id`                varchar(128)          DEFAULT NULL COMMENT '代理编号（钉钉等平台使用）',
    `public_key`              text                  DEFAULT NULL COMMENT '公钥（微信支付、支付宝等平台使用）',
    `status`                  tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0-启用；1-禁用',
    `version`                 int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `created_by`              bigint                DEFAULT NULL COMMENT '创建人ID',
    `created_at`              datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by`              bigint                DEFAULT NULL COMMENT '更新人ID',
    `updated_at`              datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    `deleted_at`              datetime(3) NULL DEFAULT NULL COMMENT '删除时间（UTC），NULL表示未删除',
    `tenant_id`               bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认，非 0 时关联 saas_tenant.id',

    `active_client_id`        varchar(128) GENERATED ALWAYS AS (
                                  IF(`deleted_at` IS NULL, `client_id`, NULL)
                                  ) STORED COMMENT '有效客户端编号，用于软删除下的唯一约束',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_social_client_tenant_type_user_client_active` (`tenant_id`, `social_type`, `user_type`, `active_client_id`),
    KEY `idx_social_client_social_type` (`social_type`),
    KEY `idx_social_client_tenant_status` (`tenant_id`, `status`),
    KEY `idx_social_client_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='社交域-社交客户端配置表';

------------------------------------------------------------------------------------------------------------------------

-- ============================================================
-- 物流域-用户与车辆关系表；多对多关联表；带时间维度，用于历史可追溯；
-- 顶级企业标准：精简审计字段（仅 created_by/created_at），
-- 不搞逻辑删除（用 effective_to_at 表达失效），不搞乐观锁，物理删除；复合主键；
-- 按租户隔离 这是顶级企业做“历史可追溯”的标准做法。对物流领域尤其重要：司机换车、车主过户、车辆报废，都需要完整历史。
-- ============================================================
DROP TABLE IF EXISTS `logistics_user_vehicle`;
CREATE TABLE `logistics_user_vehicle`
(
    `user_id`           bigint       NOT NULL COMMENT '用户ID',
    `vehicle_id`        bigint       NOT NULL COMMENT '车辆ID',
    `relation_type`     tinyint      NOT NULL COMMENT '关系类型：1-车主；2-司机；3-调度员；4-管理员',
    `effective_from_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '生效时间（UTC）',
    `tenant_id`         bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认，非 0 时关联 saas_tenant.id',
    `effective_to_at`   datetime(3) NULL DEFAULT NULL COMMENT '失效时间（UTC），NULL 表示长期有效',
    `created_by`        bigint                DEFAULT NULL COMMENT '创建人ID',
    `created_at`        datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    PRIMARY KEY (`tenant_id`, `user_id`, `vehicle_id`, `relation_type`, `effective_from_at`),
    KEY `idx_logistics_user_vehicle_vehicle_id` (`vehicle_id`),
    KEY `idx_logistics_user_vehicle_user_id` (`user_id`),
    KEY `idx_logistics_user_vehicle_relation_type` (`relation_type`),
    KEY `idx_logistics_user_vehicle_effective` (`effective_from_at`, `effective_to_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='物流域-用户与车辆关系表';

-- ============================================================
-- 物流域-车辆表；业务主表；按租户隔离；包含完整审计字段、逻辑删除、乐观锁、多租户
-- ============================================================
DROP TABLE IF EXISTS `logistics_vehicle`;
CREATE TABLE `logistics_vehicle`
(
    `id`                  bigint       NOT NULL AUTO_INCREMENT COMMENT '车辆ID',
    `vehicle_code`        varchar(64)  NOT NULL COMMENT '车辆编码，租户内唯一',
    `plate_number`        varchar(32)  NOT NULL COMMENT '车牌号，租户内唯一',
    `vehicle_type`        tinyint      NOT NULL COMMENT '车辆类型：1-厢式货车；2-平板车；3-冷藏车；4-危化品车；5-其他',
    `brand`               varchar(64)           DEFAULT NULL COMMENT '品牌',
    `model`               varchar(64)           DEFAULT NULL COMMENT '型号',
    `vin`                 varchar(32)           DEFAULT NULL COMMENT '车架号 VIN，租户内唯一',
    `engine_number`       varchar(64)           DEFAULT NULL COMMENT '发动机号',
    `load_capacity_kg`    decimal(12,3)         DEFAULT NULL COMMENT '核定载重（千克）',
    `volume_m3`           decimal(12,3)         DEFAULT NULL COMMENT '核定容积（立方米）',
    `owner_user_id`       bigint                DEFAULT NULL COMMENT '车主用户ID（冗余字段，由 logistics_user_vehicle 同步维护）',
    `status`              tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0-正常；1-停用；2-维修中；3-报废',
    `description`         varchar(500)          DEFAULT NULL COMMENT '描述',
    `version`             int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `created_by`          bigint                DEFAULT NULL COMMENT '创建人ID',
    `created_at`          datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by`          bigint                DEFAULT NULL COMMENT '更新人ID',
    `updated_at`          datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    `deleted_at`          datetime(3) NULL DEFAULT NULL COMMENT '删除时间（UTC），NULL表示未删除',
    `tenant_id`           bigint       NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认，非 0 时关联 saas_tenant.id',

    `active_vehicle_code` varchar(64) GENERATED ALWAYS AS (
                              IF(`deleted_at` IS NULL, `vehicle_code`, NULL)
                              ) STORED COMMENT '有效车辆编码，用于软删除下的唯一约束',
    `active_plate_number` varchar(32) GENERATED ALWAYS AS (
                              IF(`deleted_at` IS NULL, `plate_number`, NULL)
                              ) STORED COMMENT '有效车牌号，用于软删除下的唯一约束',
    `active_vin`          varchar(32) GENERATED ALWAYS AS (
                              IF(`deleted_at` IS NULL AND `vin` IS NOT NULL AND `vin` <> '', `vin`, NULL)
                              ) STORED COMMENT '有效车架号，用于软删除下的唯一约束',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_logistics_vehicle_tenant_code_active` (`tenant_id`, `active_vehicle_code`),
    UNIQUE KEY `uk_logistics_vehicle_tenant_plate_active` (`tenant_id`, `active_plate_number`),
    UNIQUE KEY `uk_logistics_vehicle_tenant_vin_active` (`tenant_id`, `active_vin`),
    KEY `idx_logistics_vehicle_owner_user_id` (`owner_user_id`),
    KEY `idx_logistics_vehicle_tenant_status` (`tenant_id`, `status`),
    KEY `idx_logistics_vehicle_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='物流域-车辆表';

-- =====================================================================
-- End of Realm DB Schema v1.0.0 · Global Edition (MySQL 5.7)
-- =====================================================================
