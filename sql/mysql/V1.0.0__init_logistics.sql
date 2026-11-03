-- =============================================================
-- 智慧物流运输系统 数据库 DDL
-- MySQL 8.0 / InnoDB / utf8mb4
-- =============================================================
CREATE DATABASE IF NOT EXISTS `smart_logistics`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE `smart_logistics`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

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
DROP TABLE IF EXISTS `biz_vehicle`;
CREATE TABLE biz_vehicle (
                             id                              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
                             tenant_id                       BIGINT UNSIGNED NOT NULL COMMENT '租户 ID',

    -- ===================== 主车：基础识别 =====================
                             vin                             CHAR(17)        NULL     COMMENT '主车车辆识别代号（ISO 3779:2009，17 位，字符集排除 I/O/Q）',
                             plate_no                    VARCHAR(32)     NOT NULL COMMENT '主车车牌号码',
                             plate_color                     VARCHAR(16)     NULL     COMMENT '主车车牌颜色，如 BLUE、YELLOW、GREEN',

    -- ===================== 主车：行驶证正面 =====================
                             vehicle_type                    VARCHAR(64)     NULL     COMMENT '主车车辆类型，如 重型半挂牵引车',
                             owner_name                      VARCHAR(200)    NULL     COMMENT '主车行驶证所有人',
                             owner_address                   VARCHAR(500)    NULL     COMMENT '主车行驶证住址',
                             engine_no                       VARCHAR(64)     NULL     COMMENT '主车发动机号码',
                             registration_date               DATE            NULL     COMMENT '主车注册日期',
                             issue_date                      DATE            NULL     COMMENT '主车发证日期',
                             use_character                   VARCHAR(32)     NULL     COMMENT '主车使用性质，如 货运、危化品运输',
                             model                           VARCHAR(128)    NULL     COMMENT '主车品牌型号',
                             official_seal                   VARCHAR(200)    NULL     COMMENT '主车核发机关印章名称',

    -- ===================== 主车：行驶证反面 =====================
                             archive_number                  VARCHAR(64)     NULL     COMMENT '主车档案编号',
                             gross_mass_kg                   DECIMAL(12,2)   NULL     COMMENT '主车总质量（kg）',
                             curb_weight_kg                  DECIMAL(12,2)   NULL     COMMENT '主车整备质量（kg）',
                             approved_load_kg                DECIMAL(12,2)   NULL     COMMENT '主车核定载质量（kg）',
                             approved_passengers             INT             NULL     COMMENT '主车核定载人数',
                             dimensions                      VARCHAR(64)     NULL     COMMENT '主车外廓尺寸，如 长×宽×高（mm）',
                             approved_towing_mass_kg         DECIMAL(12,2)   NULL     COMMENT '主车准牵引总质量（kg）',
                             inspection_record               VARCHAR(255)    NULL     COMMENT '主车检验记录',
                             barcode                         VARCHAR(64)     NULL     COMMENT '主车行驶证条形码',
                             fuel_type                       VARCHAR(32)     NULL     COMMENT '主车燃料种类，如 柴油、天然气、电',

    -- ===================== 主车：道路运输证 =====================
                             transport_permit_no             VARCHAR(64)     NULL     COMMENT '主车道路运输证号',
                             business_license_no             VARCHAR(64)     NULL     COMMENT '主车经营许可证号',
                             transport_issue_date            DATE            NULL     COMMENT '主车道路运输证发证日期',
                             issuing_authority               VARCHAR(128)    NULL     COMMENT '主车道路运输证核发机关',
                             transport_valid_from            DATE            NULL     COMMENT '主车道路运输证有效期起',
                             transport_valid_to              DATE            NULL     COMMENT '主车道路运输证有效期止；NULL=长期',

    -- ===================== 主车：环保与影像 =====================
                             emission_standard               VARCHAR(16)     NULL     COMMENT '主车排放标准，如 国Ⅲ、国Ⅳ、国Ⅴ、国Ⅵ',
                             driving_license_front_url       VARCHAR(1024)   NULL     COMMENT '主车行驶证正面影像 URL',
                             driving_license_back_url        VARCHAR(1024)   NULL     COMMENT '主车行驶证反面影像 URL',
                             transport_permit_url            VARCHAR(1024)   NULL     COMMENT '主车道路运输证影像 URL',
                             environmental_protection_url    VARCHAR(1024)   NULL     COMMENT '主车环保清单影像 URL',

    -- ===================== 主车：审核与运营 =====================
                             audit_status                    TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '主车资料审核状态：0=待审核，1=审核通过，2=审核驳回',
                             auditor_id                      BIGINT UNSIGNED NULL     COMMENT '主车审核人 ID',
                             auditor_name                    VARCHAR(200)    NULL     COMMENT '主车审核人姓名（冗余）',
                             audit_time                      DATETIME(3)     NULL     COMMENT '主车审核时间（UTC）',
                             audit_comments                  VARCHAR(500)    NULL     COMMENT '主车审核意见/驳回原因',
                             transport_tonnage_kg            DECIMAL(12,2)   NULL     COMMENT '主车实际拉运吨位（kg）；与核定载质量不同，用于运营结算',
                             is_default                      TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否车主默认车辆：1=是，0=否',
                             unline_oil_card                 VARCHAR(128)    NULL     COMMENT '主车线下油气卡号',

    -- ===================== 主车：数据宝核验状态 =====================
                             verification_status             TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '主车数据宝核验状态：0=未核验，1=核验中，2=核验有结果，3=核验异常，4=核验无结果，5=核验超时',
                             cert_status                     TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '主车数据宝证照状态：0=未查询，1=有效，2=无效，3=无营运信息',
                             operating_status                TINYINT UNSIGNED NOT NULL DEFAULT 10 COMMENT '主车数据宝营运状态：1=营运，2=停运，3=歇业，4=迁出，5=迁入，6=挂失，7=报废，8=注销，9=其他，10=未查询，11=无营运信息',
                             verification_fail               VARCHAR(500)    NULL     COMMENT '主车数据宝核验失败原因',
                             verification_time               DATETIME(3)     NULL     COMMENT '主车数据宝核验时间（UTC）',
                             verification_url                VARCHAR(1024)   NULL     COMMENT '主车数据宝核验状态变更回调 URL',

    -- ===================== 主车：状态与旧系统映射 =====================
                             status                          TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '主车状态：1=正常，2=停用，3=注销，4=报废',
                             legacy_vehicle_id               VARCHAR(64)     NULL     COMMENT '主车旧版卡一车车辆 ID',
                             origin                          TINYINT UNSIGNED NULL     COMMENT '主车来源：0=卡一车，1=运力中心',
                             platform                        TINYINT UNSIGNED NULL     COMMENT '主车创建平台：1=运力中心',

    -- ===================== 挂车：基础识别 =====================
                             trailer_vin                     CHAR(17)        NULL     COMMENT '挂车车辆识别代号（ISO 3779:2009，17 位）',
                             trailer_plate_number            VARCHAR(32)     NULL     COMMENT '挂车车牌号码，如 陕A1234挂',
                             trailer_plate_color             VARCHAR(16)     NULL     COMMENT '挂车车牌颜色',

    -- ===================== 挂车：行驶证 =====================
                             trailer_vehicle_type            VARCHAR(64)     NULL     COMMENT '挂车车辆类型，如 重型仓栅式半挂车',
                             trailer_owner_name              VARCHAR(200)    NULL     COMMENT '挂车行驶证所有人',
                             trailer_owner_address           VARCHAR(500)    NULL     COMMENT '挂车行驶证住址',
                             trailer_registration_date       DATE            NULL     COMMENT '挂车注册日期',
                             trailer_issue_date              DATE            NULL     COMMENT '挂车发证日期',
                             trailer_use_character           VARCHAR(32)     NULL     COMMENT '挂车使用性质',
                             trailer_model                   VARCHAR(128)    NULL     COMMENT '挂车品牌型号',
                             trailer_archive_number          VARCHAR(64)     NULL     COMMENT '挂车档案编号',
                             trailer_gross_mass_kg           DECIMAL(12,2)   NULL     COMMENT '挂车总质量（kg）',
                             trailer_curb_weight_kg          DECIMAL(12,2)   NULL     COMMENT '挂车整备质量（kg）',
                             trailer_approved_load_kg        DECIMAL(12,2)   NULL     COMMENT '挂车核定载质量（kg）',
                             trailer_dimensions              VARCHAR(64)     NULL     COMMENT '挂车外廓尺寸',
                             trailer_inspection_record       VARCHAR(255)    NULL     COMMENT '挂车检验记录',
                             trailer_barcode                 VARCHAR(64)     NULL     COMMENT '挂车行驶证条形码',

    -- ===================== 挂车：道路运输证 =====================
                             trailer_transport_permit_no     VARCHAR(64)     NULL     COMMENT '挂车道路运输证号',
                             trailer_transport_valid_from    DATE            NULL     COMMENT '挂车道路运输证有效期起',
                             trailer_transport_valid_to      DATE            NULL     COMMENT '挂车道路运输证有效期止；NULL=长期',

    -- ===================== 挂车：影像与审核 =====================
                             trailer_driving_license_front_url VARCHAR(1024) NULL     COMMENT '挂车行驶证正面影像 URL',
                             trailer_driving_license_back_url  VARCHAR(1024) NULL     COMMENT '挂车行驶证反面影像 URL',
                             trailer_transport_permit_url    VARCHAR(1024)   NULL     COMMENT '挂车道路运输证影像 URL',
                             trailer_audit_status            TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '挂车资料审核状态：0=待审核，1=审核通过，2=审核驳回',
                             trailer_audit_comments          VARCHAR(500)    NULL     COMMENT '挂车审核意见/驳回原因',

    -- ===================== 挂车：状态 =====================
                             trailer_status                  TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '挂车状态：1=正常，2=停用，3=注销，4=报废',

    -- ===================== 审计 =====================
                             created_at                      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
                             updated_at                      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间（UTC）',

                             PRIMARY KEY (id),
                             UNIQUE KEY uk_vehicle_vin (tenant_id, vin),
                             UNIQUE KEY uk_vehicle_plate (tenant_id, plate_number),
                             UNIQUE KEY uk_trailer_vin (tenant_id, trailer_vin),
                             UNIQUE KEY uk_trailer_plate (tenant_id, trailer_plate_number),
                             KEY idx_vehicle_transport_permit (tenant_id, transport_permit_no),
                             KEY idx_vehicle_status (tenant_id, status, created_at),
                             KEY idx_vehicle_audit (tenant_id, audit_status, created_at),
                             KEY idx_vehicle_cert_expire (tenant_id, transport_valid_to, cert_status),
                             KEY idx_trailer_status (tenant_id, trailer_status),
                             KEY idx_trailer_audit (tenant_id, trailer_audit_status),
                             KEY idx_vehicle_legacy (tenant_id, legacy_vehicle_id),
                             CONSTRAINT chk_vehicle_status CHECK (status IN (1,2,3,4)),
                             CONSTRAINT chk_vehicle_audit  CHECK (audit_status IN (0,1,2)),
                             CONSTRAINT chk_trailer_status CHECK (trailer_status IN (1,2,3,4)),
                             CONSTRAINT chk_trailer_audit  CHECK (trailer_audit_status IN (0,1,2)),
                             CONSTRAINT chk_vehicle_vin_char CHECK (
                                     vin IS NULL OR vin REGEXP '^[A-HJ-NPR-Z0-9]{17}$'
),
    CONSTRAINT chk_trailer_vin_char CHECK (
        trailer_vin IS NULL OR trailer_vin REGEXP '^[A-HJ-NPR-Z0-9]{17}$'
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='物流域-车辆表';


rel_driver_vehicle

rel_owner_driver

rel_owner_vehicle


-- 中国重卡运输的实际业务：
--
-- · 牵引车（主车）+ 挂车（半挂车）的组合
--
-- · 牵引车有行驶证，挂车也有独立的行驶证
--
-- · 挂车有独立的 VIN、车牌（挂字牌）、道路运输证
--
-- · 实际业务中，一辆牵引车可能换挂车（甩挂运输），一个挂车也可能被不同牵引车牵引
--
-- · 但也有很多情况是“一车一挂”固定搭配，不换挂


-- 需要区分两种业务模式：
--
-- 1.固定搭配：一辆牵引车长期配一个挂车，不换挂。这种情况下，主车+挂车可以放一张宽表（挂车字段加前缀）。
--
-- 2.甩挂运输：牵引车和挂车动态组合，今天 A 车拉 B 挂，明天 A 车拉 C 挂。这种情况下必须独立表+关系表。


-- 中国物流行业实际情况：
--
-- 很多个体车主是固定搭配，一车一挂
--
-- 但规模化的车队、甩挂运输场景确实存在
--
-- 网络货运平台通常需要支持换挂，因为运单上要记录“实际承运车辆”，牵引车和挂车可能分别记录


C 端

B 端

-- 在国际标准中，“电话号码”作为一个独立的数据概念，其正式名称几乎都是 “Phone Number”，

-- 网络货运平台与一般TMS/物流系统的核心差异在于监管合规：平台必须作为“无车承运人”承担承运责任，并向省级监测系统上传运单、资金流水、车辆和驾驶员信息，同时满足“四流合一”（合同流、资金流、票据流、轨迹流）的稽核要求。


-- 在中国的网络货运行业标准体系中，“网络货运”对应的英文表述确实是 network freight。例如，团体标准《智慧物流网络货运系统》的英文名称就是 Intelligent logistics network freight system。交通运输部的国家标准中，也将“网络货运承运经营者”译为 operator of network freight carrier platform。

-- =============================================================
-- 网络货运平台 MySQL 8.0 DDL
-- 核心约束：监管报送合规 / 四流合一 / 资金流水可追溯
-- =============================================================
CREATE DATABASE IF NOT EXISTS `network_freight`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE `network_freight`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;


DROP TABLE IF EXISTS `waybill`;

DROP TABLE IF EXISTS `fund_flow`;

DROP TABLE IF EXISTS `vehicle_track`;

DROP TABLE IF EXISTS `vat_invoice`;


-- 联合国国际贸易法委员会 (UNCITRAL)：在《汉堡规则》和《鹿特丹规则》等核心公约中，统一使用 “托运人”（Shipper） 和 “承运人”（Carrier） 这两个术语，并对它们的权利、义务和法律定义有详尽规定。
