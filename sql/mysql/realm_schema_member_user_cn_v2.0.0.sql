-- =====================================================================
-- Member 会员模块
-- =====================================================================

DROP TABLE IF EXISTS `member_user`;
CREATE TABLE `member_user`
(
    `id`                        bigint   NOT NULL AUTO_INCREMENT COMMENT '主键ID（用户ID）',

    `phone_number`              varchar(20)      NOT NULL COMMENT '手机号',
    `email`                     varchar(254)              DEFAULT NULL COMMENT '邮箱',
    `password_hash`             varchar(255)     NOT NULL COMMENT '密码哈希',
    `status`                    tinyint  NOT NULL DEFAULT 0 COMMENT '状态：0-正常；1-停用',
    `register_ip`               varchar(45)               DEFAULT NULL COMMENT '注册 IP',
    `register_terminal_type`    tinyint           DEFAULT NULL COMMENT '注册终端类型；由应用层定义',
    `last_login_ip`             varchar(45)               DEFAULT NULL COMMENT '最后登录 IP',
    `last_login_time`           datetime                  DEFAULT NULL COMMENT '最后登录时间',
    `last_login_terminal_type`  tinyint           DEFAULT NULL COMMENT '最后登录终端类型；由应用层定义',

    `failed_login_attempts`     int      NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
    `locked_until`              datetime                  DEFAULT NULL COMMENT '账号锁定截止时间；NULL 表示未锁定',
    `last_password_changed_time` datetime                 DEFAULT NULL COMMENT '密码最后修改时间',
    `phone_verified`            tinyint  NOT NULL DEFAULT 0 COMMENT '手机号是否已验证：0-否；1-是',
    `email_verified`            tinyint  NOT NULL DEFAULT 0 COMMENT '邮箱是否已验证：0-否；1-是',

    `nickname`                  varchar(64)               DEFAULT NULL COMMENT '用户昵称',
    `avatar_url`                varchar(1024)             DEFAULT NULL COMMENT '用户头像 URL',
    `full_name`                 varchar(64)               DEFAULT NULL COMMENT '真实姓名',
    `gender`                    tinyint  NOT NULL DEFAULT 0 COMMENT '性别：0-未知；1-男；2-女',
    `birth_date`                date                      DEFAULT NULL COMMENT '出生日期',
    `area_id`                   bigint            DEFAULT NULL COMMENT '所在地ID：关联 Area#id',
    `note`                      varchar(500)              DEFAULT NULL COMMENT '用户备注',

    `point_balance`             int      NOT NULL DEFAULT 0 COMMENT '积分余额；单位：积分',
    `tier_id`                   bigint            DEFAULT NULL COMMENT '会员等级ID：关联 member_tier.id',
    `experience_balance`        int      NOT NULL DEFAULT 0 COMMENT '会员经验余额；单位：经验点',
    `group_id`                  bigint            DEFAULT NULL COMMENT '用户分组ID：关联 member_group.id',

    `tenant_id`                 bigint           NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `is_deleted`                tinyint          NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
    `version`                   int              NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`                 bigint                    DEFAULT NULL COMMENT '创建人ID',
    `create_time`               datetime         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`                 bigint                    DEFAULT NULL COMMENT '更新人ID',
    `update_time`               datetime         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    `active_phone_number`       varchar(20)
        GENERATED ALWAYS AS (IF(`is_deleted` = 0, `phone_number`, NULL)) STORED
        COMMENT '有效手机号；逻辑删除下唯一约束辅助列，is_deleted=0 时为 phone_number，否则为 NULL',
    `active_email`              varchar(254)
        GENERATED ALWAYS AS (IF(`is_deleted` = 0, `email`, NULL)) STORED
        COMMENT '有效邮箱；逻辑删除下唯一约束辅助列，is_deleted=0 时为 email，否则为 NULL',

    PRIMARY KEY (`id`),

    UNIQUE KEY `uk_member_user_tenant_active_phone_number` (`tenant_id`, `active_phone_number`)
        COMMENT '租户内有效手机号唯一',
    UNIQUE KEY `uk_member_user_tenant_active_email` (`tenant_id`, `active_email`)
        COMMENT '租户内有效邮箱唯一',

    INDEX `idx_member_user_tenant_deleted_status` (`tenant_id`, `is_deleted`, `status`)
        COMMENT '逻辑删除状态查询',
    INDEX `idx_member_user_tenant_deleted_tier` (`tenant_id`, `is_deleted`, `tier_id`)
        COMMENT '逻辑删除会员等级查询',
    INDEX `idx_member_user_tenant_deleted_group` (`tenant_id`, `is_deleted`, `group_id`)
        COMMENT '逻辑删除会员分组查询',
    INDEX `idx_member_user_tenant_deleted_create_time` (`tenant_id`, `is_deleted`, `create_time`)
        COMMENT '逻辑删除创建时间查询',
    INDEX `idx_member_user_tenant_last_login_time` (`tenant_id`, `last_login_time`)
        COMMENT '最后登录时间查询',

    CONSTRAINT `chk_member_user_status` CHECK (`status` IN (0, 1)),
    CONSTRAINT `chk_member_user_gender` CHECK (`gender` IN (0, 1, 2)),
    CONSTRAINT `chk_member_user_phone_verified` CHECK (`phone_verified` IN (0, 1)),
    CONSTRAINT `chk_member_user_email_verified` CHECK (`email_verified` IN (0, 1))
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='会员-用户表';

DROP TABLE IF EXISTS `member_tag`;
CREATE TABLE `member_tag`
(
    `id`          bigint  NOT NULL AUTO_INCREMENT COMMENT '主键ID（标签ID）',
    `name`        varchar(128)    NOT NULL COMMENT '标签名称',

    `tenant_id`   bigint          NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `is_deleted`  tinyint         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
    `version`     int             NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`   bigint                   DEFAULT NULL COMMENT '创建人ID',
    `create_time` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   bigint                   DEFAULT NULL COMMENT '更新人ID',
    `update_time` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    `active_name` varchar(128)
                  GENERATED ALWAYS AS (IF(`is_deleted` = 0, `name`, NULL)) STORED
        COMMENT '有效标签名称；逻辑删除下唯一约束辅助列，is_deleted=0 时为 name，否则为 NULL',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_member_tag_tenant_active_name` (`tenant_id`, `active_name`) COMMENT '租户内有效标签名称唯一',
    INDEX `idx_member_tag_tenant_name` (`tenant_id`, `name`) COMMENT '标签名称查询',
    INDEX `idx_member_tag_tenant_deleted` (`tenant_id`, `is_deleted`) COMMENT '逻辑删除查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='会员-标签表';

DROP TABLE IF EXISTS `member_user_tag`;
CREATE TABLE `member_user_tag`
(
    `id`          bigint  NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`     bigint  NOT NULL COMMENT '用户ID：关联 member_user.id',
    `tag_id`      bigint  NOT NULL COMMENT '标签ID：关联 member_tag.id',

    `tenant_id`   bigint          NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `create_by`   bigint                   DEFAULT NULL COMMENT '创建人ID',
    `create_time` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_member_user_tag_tenant_user_tag` (`tenant_id`, `user_id`, `tag_id`)
        COMMENT '租户内同一用户同一标签唯一',
    INDEX `idx_member_user_tag_tenant_tag` (`tenant_id`, `tag_id`) COMMENT '标签查询',
    INDEX `idx_member_user_tag_tenant_user` (`tenant_id`, `user_id`) COMMENT '用户查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='会员-用户标签关联表';

DROP TABLE IF EXISTS `member_check_in_transaction`;
CREATE TABLE `member_check_in_transaction`
(
    `id`                bigint   NOT NULL AUTO_INCREMENT COMMENT '主键ID（签到流水ID）',
    `user_id`           bigint   NOT NULL COMMENT '签到用户ID：关联 member_user.id',
    `check_in_date`     date             NOT NULL COMMENT '签到日期',
    `streak_day`        smallint  NOT NULL COMMENT '连续签到第几天；从 1 开始',
    `reward_point`      int      NOT NULL DEFAULT 0 COMMENT '签到奖励积分；单位：积分',
    `reward_experience` int      NOT NULL DEFAULT 0 COMMENT '签到奖励经验；单位：经验点',
    `idempotency_key`   varchar(64)               DEFAULT NULL COMMENT '幂等键；NULL 表示不启用幂等',

    `tenant_id`         bigint           NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `create_time`       datetime         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_member_check_in_transaction_tenant_user_date` (`tenant_id`, `user_id`, `check_in_date`)
        COMMENT '租户内同一用户同一签到日期唯一',
    UNIQUE KEY `uk_member_check_in_transaction_tenant_idempotency` (`tenant_id`, `idempotency_key`)
        COMMENT '租户内幂等键唯一',
    INDEX `idx_member_check_in_transaction_tenant_user_time` (`tenant_id`, `user_id`, `create_time`)
        COMMENT '用户签到时间查询',
    CONSTRAINT `chk_member_check_in_transaction_streak_day` CHECK (`streak_day` >= 1)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='会员-签到流水表';

DROP TABLE IF EXISTS `member_check_in_rule`;
CREATE TABLE `member_check_in_rule`
(
    `id`                bigint   NOT NULL AUTO_INCREMENT COMMENT '主键ID（规则ID）',
    `day_index`         smallint  NOT NULL COMMENT '签到第几天；从 1 开始',
    `reward_point`      int      NOT NULL DEFAULT 0 COMMENT '奖励积分；单位：积分',
    `reward_experience` int      NOT NULL DEFAULT 0 COMMENT '奖励经验；单位：经验点',
    `status`            tinyint  NOT NULL DEFAULT 0 COMMENT '状态：0-正常；1-停用',

    `tenant_id`         bigint           NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `is_deleted`        tinyint          NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
    `version`           int              NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`         bigint                    DEFAULT NULL COMMENT '创建人ID',
    `create_time`       datetime         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`         bigint                    DEFAULT NULL COMMENT '更新人ID',
    `update_time`       datetime         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    `active_day_index`  smallint
                        GENERATED ALWAYS AS (IF(`is_deleted` = 0, `day_index`, NULL)) STORED
        COMMENT '有效签到天数；逻辑删除下唯一约束辅助列，is_deleted=0 时为 day_index，否则为 NULL',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_member_check_in_rule_tenant_active_day_index` (`tenant_id`, `active_day_index`)
        COMMENT '租户内有效签到天数唯一',
    INDEX `idx_member_check_in_rule_tenant_status` (`tenant_id`, `status`) COMMENT '状态查询',
    INDEX `idx_member_check_in_rule_tenant_deleted` (`tenant_id`, `is_deleted`) COMMENT '逻辑删除查询',
    CONSTRAINT `chk_member_check_in_rule_day_index` CHECK (`day_index` >= 1),
    CONSTRAINT `chk_member_check_in_rule_status` CHECK (`status` IN (0, 1))
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='会员-签到规则表';

DROP TABLE IF EXISTS `member_point_transaction`;
CREATE TABLE `member_point_transaction`
(
    `id`              bigint   NOT NULL AUTO_INCREMENT COMMENT '主键ID（积分流水ID）',
    `user_id`         bigint   NOT NULL COMMENT '用户ID：关联 member_user.id',
    `reference_code`  varchar(255)              DEFAULT NULL COMMENT '业务参考编码',
    `event_type`      tinyint  NOT NULL COMMENT '事件类型；由应用层定义',
    `title`           varchar(255)     NOT NULL COMMENT '积分标题',
    `description`     varchar(255)              DEFAULT NULL COMMENT '积分描述',
    `point_delta`     int              NOT NULL COMMENT '积分变动值；正数获得，负数消耗',
    `point_balance`   int      NOT NULL COMMENT '变动后的积分余额；单位：积分',
    `idempotency_key` varchar(64)               DEFAULT NULL COMMENT '幂等键；NULL 表示不启用幂等',

    `tenant_id`       bigint           NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `create_time`     datetime         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_member_point_transaction_tenant_idempotency` (`tenant_id`, `idempotency_key`)
        COMMENT '租户内幂等键唯一',
    INDEX `idx_member_point_transaction_tenant_user_time` (`tenant_id`, `user_id`, `create_time`)
        COMMENT '用户积分流水查询',
    INDEX `idx_member_point_transaction_tenant_reference` (`tenant_id`, `reference_code`, `event_type`)
        COMMENT '业务编码与事件类型查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='会员-积分流水表';

DROP TABLE IF EXISTS `member_tier`;
CREATE TABLE `member_tier`
(
    `id`                  bigint   NOT NULL AUTO_INCREMENT COMMENT '主键ID（等级ID）',
    `name`                varchar(64)      NOT NULL COMMENT '等级名称',
    `tier_value`          smallint  NOT NULL COMMENT '等级值；数值越大等级越高',
    `required_experience` int      NOT NULL DEFAULT 0 COMMENT '升级所需经验；单位：经验点',
    `discount_percent`    tinyint  NOT NULL DEFAULT 100 COMMENT '折扣百分比；单位：%；100=无折扣，90=9折',
    `icon_url`            varchar(255)              DEFAULT NULL COMMENT '等级图标 URL',
    `background_url`      varchar(255)              DEFAULT NULL COMMENT '等级背景图 URL',
    `status`              tinyint  NOT NULL DEFAULT 0 COMMENT '状态：0-正常；1-停用',

    `tenant_id`           bigint           NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `is_deleted`          tinyint          NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
    `version`             int              NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`           bigint                    DEFAULT NULL COMMENT '创建人ID',
    `create_time`         datetime         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`           bigint                    DEFAULT NULL COMMENT '更新人ID',
    `update_time`         datetime         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    `active_name`         varchar(64)
                          GENERATED ALWAYS AS (IF(`is_deleted` = 0, `name`, NULL)) STORED
        COMMENT '有效等级名称；逻辑删除下唯一约束辅助列，is_deleted=0 时为 name，否则为 NULL',
    `active_tier_value`   smallint
                          GENERATED ALWAYS AS (IF(`is_deleted` = 0, `tier_value`, NULL)) STORED
        COMMENT '有效等级值；逻辑删除下唯一约束辅助列，is_deleted=0 时为 tier_value，否则为 NULL',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_member_tier_tenant_active_name` (`tenant_id`, `active_name`) COMMENT '租户内有效等级名称唯一',
    UNIQUE KEY `uk_member_tier_tenant_active_tier_value` (`tenant_id`, `active_tier_value`) COMMENT '租户内有效等级值唯一',
    INDEX `idx_member_tier_tenant_status` (`tenant_id`, `status`) COMMENT '状态查询',
    INDEX `idx_member_tier_tenant_deleted` (`tenant_id`, `is_deleted`) COMMENT '逻辑删除查询',
    CONSTRAINT `chk_member_tier_status` CHECK (`status` IN (0, 1))
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='会员-等级表';

DROP TABLE IF EXISTS `member_tier_history`;
CREATE TABLE `member_tier_history`
(
    `id`               bigint   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`          bigint   NOT NULL COMMENT '用户ID：关联 member_user.id',
    `tier_id`          bigint   NOT NULL COMMENT '等级ID：关联 member_tier.id',
    `tier_value`       smallint  NOT NULL COMMENT '会员等级值；冗余 member_tier.tier_value',
    `discount_percent` tinyint  NOT NULL DEFAULT 100 COMMENT '折扣百分比；冗余 member_tier.discount_percent',
    `tier_experience`  int      NOT NULL DEFAULT 0 COMMENT '升级经验；冗余 member_tier.required_experience',
    `user_experience`  int      NOT NULL DEFAULT 0 COMMENT '会员当前经验；单位：经验点',
    `note`             varchar(500)              DEFAULT NULL COMMENT '备注',
    `description`      varchar(500)              DEFAULT NULL COMMENT '描述',

    `tenant_id`        bigint           NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `create_time`      datetime         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (`id`),
    INDEX `idx_member_tier_history_tenant_user_time` (`tenant_id`, `user_id`, `create_time`)
        COMMENT '用户等级历史查询',
    INDEX `idx_member_tier_history_tenant_tier` (`tenant_id`, `tier_id`)
        COMMENT '等级查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='会员-等级历史表';

DROP TABLE IF EXISTS `member_experience_transaction`;
CREATE TABLE `member_experience_transaction`
(
    `id`                 bigint   NOT NULL AUTO_INCREMENT COMMENT '主键ID（经验流水ID）',
    `user_id`            bigint   NOT NULL COMMENT '用户ID：关联 member_user.id',
    `event_type`         tinyint  NOT NULL COMMENT '事件类型；由应用层定义',
    `reference_code`     varchar(255)              DEFAULT NULL COMMENT '业务参考编码',
    `title`              varchar(255)     NOT NULL COMMENT '标题',
    `description`        varchar(500)              DEFAULT NULL COMMENT '描述',
    `experience_delta`   int              NOT NULL COMMENT '经验变动值；正数增加，负数扣减',
    `experience_balance` int      NOT NULL COMMENT '变更后的经验余额；单位：经验点',
    `idempotency_key`    varchar(64)               DEFAULT NULL COMMENT '幂等键；NULL 表示不启用幂等',

    `tenant_id`          bigint           NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `create_time`        datetime         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_member_experience_transaction_tenant_idempotency` (`tenant_id`, `idempotency_key`)
        COMMENT '租户内幂等键唯一',
    INDEX `idx_member_experience_transaction_tenant_user_time` (`tenant_id`, `user_id`, `create_time`)
        COMMENT '用户经验流水查询',
    INDEX `idx_member_experience_transaction_tenant_reference` (`tenant_id`, `reference_code`, `event_type`)
        COMMENT '业务编码与事件类型查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='会员-经验流水表';

DROP TABLE IF EXISTS `member_group`;
CREATE TABLE `member_group`
(
    `id`          bigint  NOT NULL AUTO_INCREMENT COMMENT '主键ID（分组ID）',
    `name`        varchar(64)     NOT NULL COMMENT '分组名称',
    `note`        varchar(500)             DEFAULT NULL COMMENT '备注',
    `status`      tinyint  NOT NULL DEFAULT 0 COMMENT '状态：0-正常；1-停用',

    `tenant_id`   bigint          NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `is_deleted`  tinyint         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
    `version`     int             NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`   bigint                   DEFAULT NULL COMMENT '创建人ID',
    `create_time` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   bigint                   DEFAULT NULL COMMENT '更新人ID',
    `update_time` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    `active_name` varchar(64)
                  GENERATED ALWAYS AS (IF(`is_deleted` = 0, `name`, NULL)) STORED
        COMMENT '有效分组名称；逻辑删除下唯一约束辅助列，is_deleted=0 时为 name，否则为 NULL',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_member_group_tenant_active_name` (`tenant_id`, `active_name`) COMMENT '租户内有效分组名称唯一',
    INDEX `idx_member_group_tenant_status` (`tenant_id`, `status`) COMMENT '状态查询',
    INDEX `idx_member_group_tenant_deleted` (`tenant_id`, `is_deleted`) COMMENT '逻辑删除查询',
    CONSTRAINT `chk_member_group_status` CHECK (`status` IN (0, 1))
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='会员-分组表';

DROP TABLE IF EXISTS `member_setting`;
CREATE TABLE `member_setting`
(
    `id`            bigint  NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `setting_key`   varchar(64)     NOT NULL COMMENT '配置键；如 point.deduction.enabled、point.earning.rate',
    `setting_value` varchar(1024)            DEFAULT NULL COMMENT '配置值；字符串存储，由应用层解析类型',
    `description`   varchar(255)             DEFAULT NULL COMMENT '配置说明',

    `tenant_id`     bigint          NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `is_deleted`    tinyint         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
    `version`       int             NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`     bigint                   DEFAULT NULL COMMENT '创建人ID',
    `create_time`   datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     bigint                   DEFAULT NULL COMMENT '更新人ID',
    `update_time`   datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    `active_setting_key` varchar(64)
        GENERATED ALWAYS AS (IF(`is_deleted` = 0, `setting_key`, NULL)) STORED
        COMMENT '有效配置键；逻辑删除下唯一约束辅助列，is_deleted=0 时为 setting_key，否则为 NULL',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_member_setting_tenant_active_setting_key` (`tenant_id`, `active_setting_key`) COMMENT '租户内有效配置键唯一',
    INDEX `idx_member_setting_tenant_deleted` (`tenant_id`, `is_deleted`) COMMENT '逻辑删除查询'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='会员-租户配置表';

DROP TABLE IF EXISTS `member_address`;
CREATE TABLE `member_address`
(
    `id`             bigint   NOT NULL AUTO_INCREMENT COMMENT '主键ID（地址ID）',
    `user_id`        bigint   NOT NULL COMMENT '用户ID：关联 member_user.id',
    `recipient_name` varchar(64)      NOT NULL COMMENT '收件人姓名',
    `phone_number`   varchar(20)      NOT NULL COMMENT '手机号',
    `area_id`        bigint            DEFAULT NULL COMMENT '地区ID：关联 Area#id',
    `address_line1`  varchar(255)     NOT NULL COMMENT '地址第一行；街道、门牌号',
    `address_line2`  varchar(255)              DEFAULT NULL COMMENT '地址第二行；楼栋、单元、房间号等',
    `is_default`     tinyint  NOT NULL DEFAULT 0 COMMENT '是否默认地址：0-否；1-是',

    `tenant_id`      bigint           NOT NULL DEFAULT 0 COMMENT '租户ID，0 表示全局默认；关联 saas_tenant.id',
    `is_deleted`     tinyint          NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
    `version`        int              NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `create_by`      bigint                    DEFAULT NULL COMMENT '创建人ID',
    `create_time`    datetime         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      bigint                    DEFAULT NULL COMMENT '更新人ID',
    `update_time`    datetime         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    `active_default_user` bigint
        GENERATED ALWAYS AS (IF(`is_deleted` = 0 AND `is_default` = 1, `user_id`, NULL)) STORED
        COMMENT '有效默认地址用户ID；逻辑删除下唯一约束辅助列，is_deleted=0 且 is_default=1 时为 user_id，否则为 NULL',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_member_address_tenant_active_default_user` (`tenant_id`, `active_default_user`)
        COMMENT '租户内同一用户至多一个有效默认地址',
    INDEX `idx_member_address_tenant_user` (`tenant_id`, `user_id`) COMMENT '用户地址查询',
    INDEX `idx_member_address_tenant_deleted` (`tenant_id`, `is_deleted`) COMMENT '逻辑删除查询',
    CONSTRAINT `chk_member_address_is_default` CHECK (`is_default` IN (0, 1))
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC COMMENT='会员-收件地址表';
