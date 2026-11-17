
核心差异：目的决定一切
维度	操作日志表（Operational Log）	审计表（Audit Log）
主要读者	开发、运维、SRE	审计员、监管方、法务、安全调查人员
回答的问题	“系统为什么出错了？性能瓶颈在哪？”	“谁在何时对哪条业务数据做了什么，是否合规？”
内容偏向	技术细节：堆栈、耗时、SQL、缓存命中、重试次数	业务语义：order.delete、user.role_change、操作结果
可变性	可轮转、可采样、可清理、可压缩	只追加，不可修改，不可删除（保留期内）
留存要求	通常 7～30 天，按容量滚动	等保 ≥6 个月，金融/医疗通常 5 年以上
法律地位	排查工具，不承担举证责任	可作为合规证据，篡改即失效
失败操作	记录错误堆栈即可	必须记录 outcome=DENIED/FAILURE，越权尝试本身就是审计对象
一句话概括：操作日志是给工程师“修 bug”用的，审计表是给监管方“查责任”用的。 前者的价值在于“够用就好、及时清理”，后者的价值恰恰在于“不可清理、不可篡改”。



操作日志表：Interval 分区 + 滚动清理

CREATE TABLE operation_logs (
                                log_id            NUMBER GENERATED ALWAYS AS IDENTITY,
                                log_time          TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,

    -- 关联链路
                                request_id        VARCHAR2(64),          -- 一次入站请求的关联标识
                                trace_id          VARCHAR2(64),          -- 分布式链路 trace
                                span_id           VARCHAR2(32),          -- 当前 span
                                tenant_id         NUMBER          NOT NULL,

    -- 操作主体（与技术诊断相关）
                                actor_id          NUMBER,
                                operation_name    VARCHAR2(128)   NOT NULL,  -- 如 placeOrder / refund
                                operation_status  VARCHAR2(16)    NOT NULL,  -- SUCCESS / FAILURE / TIMEOUT

    -- 技术诊断维度（审计表不承载这些）
                                duration_ms       NUMBER,
                                sql_fingerprint   VARCHAR2(64),          -- 归一化 SQL 指纹，不存明文
                                error_code        VARCHAR2(64),
                                error_stack       CLOB,                   -- SecureFile + 压缩
                                client_ip         VARCHAR2(45),
                                user_agent        VARCHAR2(512),
                                log_level         VARCHAR2(8)     NOT NULL,  -- DEBUG / INFO / WARN / ERROR

                                CONSTRAINT pk_operation_logs PRIMARY KEY (log_id, log_time)
)
    PARTITION BY RANGE (log_time)
INTERVAL (NUMTODSINTERVAL(1, 'DAY'))       -- 按天自动创建分区
(
  PARTITION p_seed VALUES LESS THAN (TIMESTAMP '2026-01-01 00:00:00 +00:00')
)
TABLESPACE ts_operation_logs
LOB (error_stack) STORE AS SECUREFILE (COMPRESS MEDIUM)
ROW STORE COMPRESS ADVANCED;

-- 本地索引：分区 DROP 时不会留下不可用的全局索引
CREATE INDEX idx_oplog_request ON operation_logs (request_id) LOCAL;
CREATE INDEX idx_oplog_time_status ON operation_logs (log_time, operation_status) LOCAL;


-- 操作类型（Action）和操作人（Actor）是描述一个事件的、不可分割的两个维度。

-- 在国际规范和企业实践中，更常见的命名是 actor（或其 ID 形式 actor_id）它对标的是审计/结构化日志领域事实上的通用字段 actor

-- 首选：actorId（Java 风格）actor_id 不会变，actor_name 会。

-- 参数是 String，就叫 actorName；哪天参数升级成 Actor 对象（含 id/name/type），再叫 actor。

-- 每个字段都精确地回答“谁、做了什么、对什么、何时、结果如何”，并且表本身在存储层被设计为不可变。

-- 让每一条记录在事后审查（包括合规审计、争议举证、安全事件复盘）时能够独立回答：谁、在什么上下文下、对什么对象、做了什么、结果如何、改前改后是什么。


核心字段（不可妥协）
这组字段对应审计事件的“5W”模型——Who（谁）、What（做了什么）、Target（对什么）、When（何时）、Outcome（结果如何），是任何一张审计日志表都应当包含的最低限度字段。

字段	类型建议	说明
event_id	UUID（推荐 UUIDv7）	全局唯一事件标识。UUIDv7 自带时间有序性，对索引写入更友好，也便于分布式环境下生成。
occurred_at	TIMESTAMPTZ（UTC）	事件发生的精确时间。始终以 UTC 存储，展示层再按时区转换；避免使用无时区的 TIMESTAMP，否则跨时区排查时会产生歧义。
actor_id	VARCHAR / BIGINT	操作主体的唯一标识（用户 ID、服务账号 ID、系统标识）。这是“谁”的主字段，必须是可追溯的。
actor_display_name	VARCHAR	操作主体的可读名称（如用户名、服务名）。不要省略这个字段。用户可能改名、离职、被删除，事后审计时仅有 actor_id 无法让审计人员理解“这是谁”；保留事件发生时的显示名，是让日志“对人类可解释”的关键冗余。
action	VARCHAR	描述发生了什么，推荐采用 resource.verb 格式，例如 order.create、document.delete、user.login。统一格式让后续按动作类型筛选和告警变得可控。
resource_type	VARCHAR	被操作资源的类型（如 order、document、user）。
resource_id	VARCHAR	被操作资源的具体标识。与 resource_type 组合，精确定位“对什么做了操作”。
outcome	VARCHAR 枚举	操作结果，如 SUCCESS / FAILURE / DENIED。这是安全监控的关键字段——失败的敏感操作（如批量删除被拒、越权尝试）往往比成功的操作更值得关注。
这组字段合在一起，已经能独立回答最基本的审计问题：“张三在什么时间，对哪个订单，执行了删除，结果是成功还是失败。


审计表：Blockchain Table，把不可变性交给引擎

CREATE BLOCKCHAIN TABLE audit_logs (
  -- 多租户隔离
  tenant_id           NUMBER          NOT NULL,

  -- 事件标识与时间
  event_id            RAW(16)         NOT NULL,        -- 应用层生成 UUID，RAW 比 CHAR(36) 更省空间
  -- 时间：与 UNIFIED_AUDIT_TRAIL.EVENT_TIMESTAMP 类型对齐
  occurred_at         TIMESTAMP(6) WITH TIME ZONE NOT NULL,  -- 带时区，UTC 写入

  -- 等保核心维度
 -- 等保核心：谁、做了什么、对什么、结果如何
  actor_id            NUMBER          NOT NULL,
  actor_display_name  VARCHAR2(128)   NOT NULL,        -- 事件发生时的不可变快照
  action_name         VARCHAR2(128)   NOT NULL,        -- resource.verb
  outcome             VARCHAR2(16)    NOT NULL,        -- SUCCESS / FAILURE / DENIED

  -- 目标资源
  resource_type       VARCHAR2(64)    NOT NULL,
  resource_id         VARCHAR2(128)   NOT NULL,

  -- 上下文
  source_ip           VARCHAR2(45),
  user_agent          VARCHAR2(512),
  request_id          VARCHAR2(64),                         -- 与 operation_logs 对齐
  session_id          VARCHAR2(128),

  -- 变更快照
  diff_json           CLOB,                            -- 字段级 before/after
  CONSTRAINT chk_diff_json CHECK (diff_json IS JSON),

  -- 主键
  CONSTRAINT pk_audit_logs PRIMARY KEY (tenant_id, event_id)
)
NO DROP UNTIL 365 DAYS IDLE
NO DELETE UNTIL 3650 DAYS AFTER INSERT
HASHING USING "SHA2_512" VERSION "V2";


CREATE TABLE kyc_operation_audit_log (
    -- ========== 主键与时间 ==========
                                         id              BIGINT UNSIGNED AUTO_INCREMENT COMMENT '自增主键',
                                         occurred_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '事件发生时间（UTC，毫秒精度）',
                                         recorded_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '日志写入时间（UTC）',

    -- ========== 事件标识 ==========
                                         event_id        CHAR(36)        NOT NULL COMMENT '全局唯一事件ID（UUID），用于跨系统关联',
                                         event_version   VARCHAR(8)      NOT NULL DEFAULT '1.0' COMMENT '事件Schema版本',

    -- ========== 主体（Actor） ==========
                                         actor_id        VARCHAR(64)     NOT NULL COMMENT '操作人ID（内部稳定ID，非姓名）',
                                         actor_type      VARCHAR(16)     NOT NULL DEFAULT 'USER' COMMENT 'USER / SERVICE / SYSTEM / AI_AGENT',
                                         actor_name      VARCHAR(128)    NULL COMMENT '操作人名称快照（仅展示辅助）',
                                         on_behalf_of    VARCHAR(64)     NULL COMMENT '代理场景：实际受益人ID（服务账号代用户操作时）',

    -- ========== 动作（Action） ==========
                                         action          VARCHAR(64)     NOT NULL COMMENT '过去时动作，如 GOODS_LEVEL_CONFIG.CREATED',
                                         event_category  VARCHAR(32)     NOT NULL COMMENT 'CONFIG / AUTH / DATA_ACCESS / PERMISSION / FINANCIAL',
                                         read_only       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '1-只读操作；0-写操作',

    -- ========== 客体（Target） ==========
                                         target_type     VARCHAR(64)     NOT NULL COMMENT '对象类型，如 GOODS_LEVEL_CONFIG',
                                         target_id       VARCHAR(64)     NOT NULL COMMENT '对象稳定ID',
                                         target_name     VARCHAR(256)    NULL COMMENT '对象名称快照（辅助定位）',

    -- ========== 结果（Outcome） ==========
                                         outcome         VARCHAR(16)     NOT NULL COMMENT 'SUCCESS / DENIED / FAILURE',
                                         error_code      VARCHAR(64)     NULL COMMENT '失败时的错误码',
                                         error_message   VARCHAR(512)    NULL COMMENT '失败简述（不包含敏感数据）',

    -- ========== 变更快照（仅写操作） ==========
                                         before_data     JSON            NULL COMMENT '变更前快照（仅变更类操作）',
                                         after_data      JSON            NULL COMMENT '变更后快照（仅变更类操作）',
                                         changed_fields  JSON            NULL COMMENT '发生变化的字段名数组，便于快速筛选',

    -- ========== 上下文（Context） ==========
                                         request_id      VARCHAR(64)     NULL COMMENT '请求链路ID（traceId / MDC）',
                                         session_id      VARCHAR(128)    NULL COMMENT '会话或Token ID（脱敏后）',
                                         source_ip       VARCHAR(45)     NULL COMMENT '源IP（IPv4/IPv6）',
                                         user_agent      VARCHAR(512)    NULL COMMENT '客户端User-Agent',
                                         service_name    VARCHAR(64)     NULL COMMENT '应用/服务名',
                                         extra           JSON            NULL COMMENT '扩展字段，避免频繁DDL',

    -- ========== 完整性（可选但推荐） ==========
                                         prev_hash       CHAR(64)        NULL COMMENT '上一条记录的哈希（SHA-256）',
                                         record_hash     CHAR(64)        NULL COMMENT '本条记录哈希，用于链式校验',

                                         PRIMARY KEY (id, occurred_at),
                                         UNIQUE KEY uk_event_id (event_id, occurred_at),
                                         KEY idx_target (target_type, target_id, occurred_at),
                                         KEY idx_actor (actor_id, occurred_at),
                                         KEY idx_action (action, occurred_at),
                                         KEY idx_outcome (outcome, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT='操作审计日志（append-only）'
PARTITION BY RANGE (TO_DAYS(occurred_at)) (
    PARTITION p2026_q4 VALUES LESS THAN (TO_DAYS('2027-01-01')),
    PARTITION p2027_q1 VALUES LESS THAN (TO_DAYS('2027-04-01')),
    PARTITION p2027_q2 VALUES LESS THAN (TO_DAYS('2027-07-01')),
    PARTITION p2027_q3 VALUES LESS THAN (TO_DAYS('2027-10-01')),
    PARTITION p2027_q4 VALUES LESS THAN (TO_DAYS('2028-01-01')),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);


CREATE TABLE KYC_OPERATION_AUDIT_LOG (
    -- ========== 主键与时间 ==========
                                         ID              VARCHAR2(64)    NOT NULL ENABLE,   -- 用 VARCHAR2 方便代码里 BaseUtil.generateID() 赋值
                                         OCCURRED_AT     TIMESTAMP(3)    DEFAULT SYSTIMESTAMP NOT NULL ENABLE,
                                         RECORDED_AT     TIMESTAMP(3)    DEFAULT SYSTIMESTAMP NOT NULL ENABLE,

    -- ========== 事件标识 ==========
                                         EVENT_ID        VARCHAR2(36)    NOT NULL ENABLE,
                                         EVENT_VERSION   VARCHAR2(8)     DEFAULT '1.0' NOT NULL ENABLE,

    -- ========== 主体（Actor） ==========
                                         ACTOR_ID        VARCHAR2(64)    NOT NULL ENABLE,
                                         ACTOR_TYPE      VARCHAR2(16)    DEFAULT 'USER' NOT NULL ENABLE,
                                         ACTOR_NAME      VARCHAR2(128),
                                         ON_BEHALF_OF    VARCHAR2(64),

    -- ========== 动作（Action） ==========
                                         ACTION          VARCHAR2(64)    NOT NULL ENABLE,
                                         EVENT_CATEGORY  VARCHAR2(32)    NOT NULL ENABLE,
                                         READ_ONLY       NUMBER(1,0)     DEFAULT 0 NOT NULL ENABLE,

    -- ========== 客体（Target） ==========
                                         TARGET_TYPE     VARCHAR2(64)    NOT NULL ENABLE,
                                         TARGET_ID       VARCHAR2(64)    NOT NULL ENABLE,
                                         TARGET_NAME     VARCHAR2(256),

    -- ========== 结果（Outcome） ==========
                                         OUTCOME         VARCHAR2(16)    NOT NULL ENABLE,
                                         ERROR_CODE      VARCHAR2(64),
                                         ERROR_MESSAGE   VARCHAR2(512),

    -- ========== 变更快照 ==========
                                         BEFORE_DATA     CLOB,
                                         AFTER_DATA      CLOB,
                                         CHANGED_FIELDS  CLOB,

    -- ========== 上下文（Context） ==========
                                         REQUEST_ID      VARCHAR2(64),
                                         SESSION_ID      VARCHAR2(128),
                                         SOURCE_IP       VARCHAR2(45),
                                         USER_AGENT      VARCHAR2(512),
                                         SERVICE_NAME    VARCHAR2(64),
                                         EXTRA           CLOB,

    -- ========== 完整性 ==========
                                         PREV_HASH       VARCHAR2(64),
                                         RECORD_HASH     VARCHAR2(64),

                                         PRIMARY KEY (ID, OCCURRED_AT)
)
-- 按时间分区（如果不需要分区，可删掉下面这段）
    PARTITION BY RANGE (OCCURRED_AT) (
    PARTITION P2026_Q4 VALUES LESS THAN (TO_DATE('2027-01-01', 'YYYY-MM-DD')),
    PARTITION P2027_Q1 VALUES LESS THAN (TO_DATE('2027-04-01', 'YYYY-MM-DD')),
    PARTITION P2027_Q2 VALUES LESS THAN (TO_DATE('2027-07-01', 'YYYY-MM-DD')),
    PARTITION P2027_Q3 VALUES LESS THAN (TO_DATE('2027-10-01', 'YYYY-MM-DD')),
    PARTITION P2027_Q4 VALUES LESS THAN (TO_DATE('2028-01-01', 'YYYY-MM-DD')),
    PARTITION P_FUTURE VALUES LESS THAN (MAXVALUE)
);

-- 建索引
CREATE INDEX IDX_LOG_TARGET ON KYC_OPERATION_AUDIT_LOG (TARGET_TYPE, TARGET_ID, OCCURRED_AT);
CREATE INDEX IDX_LOG_ACTOR  ON KYC_OPERATION_AUDIT_LOG (ACTOR_ID, OCCURRED_AT);
CREATE INDEX IDX_LOG_ACTION ON KYC_OPERATION_AUDIT_LOG (ACTION, OCCURRED_AT);



-- 行业里常用的规范是 audit_log（审计日志）、action_log（动作日志）、event_log（事件日志）。

-- 综合国际标准和全球领先企业的公开规范，更推荐使用 old_data 和 new_data。

-- 强烈推荐使用 module_code 和 action_code（字符串语义化编码）
-- 在国际标准（如 ISO 20000 审计规范）和全球领先企业（Google、阿里、腾讯、Netflix）的日志系统最佳实践中，“自解释（Self-documenting）的字符串编码”是日志表设计的绝对铁律。

-- occurred_at 是 GitHub、Stripe、AWS 审计日志中最常用的字段名。

-- 综合国际标准和审计/事件溯源的最佳实践，强烈推荐使用 reason。

-- 在国际标准（如领域驱动设计 DDD 和事件溯源架构）中，entity_type 存的是业务实体类型（Domain Entity Type），通常用大写的语义化英文单数表示，比如 VEHICLE、DRIVER、GOODS、RULE_CONFIG。
-- GitHub Audit Log：包含 action, actor, created_at, event, repo, 甚至是组合拼装的描述，但如果是结构化设计，通常用 summary。
-- 综合国际标准（如架构设计中的事件溯源、CQRS 模式）以及全球大厂（Google、阿里、腾讯）的实践，最强推 event_log（事件日志）或 change_log（变更日志）
-- 在国际标准和企业级架构（如 Google Cloud Events、AWS EventBridge、阿里云事件总线）中，强烈建议直接使用 module 和 action。

/* =========================================================================================
 * 统一业务事件日志表 (Business Event Log)
 * 【设计标准】：国际事件溯源（Event Sourcing）规范 / 企业级审计规范
 * ========================================================================================= */
CREATE TABLE kyc_event_log (
                               id VARCHAR2(64) NOT NULL,

    -- 业务分类路由
                               module VARCHAR2(64) NOT NULL,                 -- 模块名称 (如: GROWTH_VALUE, GOODS_RATING)
                               action VARCHAR2(64) NOT NULL,                 -- 动作名称 (如: MANUAL_ADJUSTED, SCORE_UPDATED)

    -- 实体对象定位
                               entity_type VARCHAR2(64) NOT NULL,            -- 实体类型 (如: VEHICLE, DRIVER, GOODS, GOODS_RULE)
                               entity_id VARCHAR2(64) NOT NULL,              -- 实体对象ID
                               entity_name VARCHAR2(512),                    -- 实体对象名称快照 (如: 车牌号、货源名称)

    -- 变更数据快照
                               old_data CLOB,                                -- 变更前数据 (JSON格式)
                               new_data CLOB,                                -- 变更后数据 (JSON格式)
                               summary VARCHAR2(2000),                       -- 动作摘要 (如: "得分由85降至75，等级由S降为A")

    -- 溯源信息 (Actor 模式)
                               reason VARCHAR2(512),                         -- 操作原因/备注
                               actor_id VARCHAR2(64) NOT NULL,               -- 执行者ID (操作人)
                               actor_name VARCHAR2(512) NOT NULL,            -- 执行者姓名

                               occurred_at DATE DEFAULT SYSDATE NOT NULL,   -- 事件发生时间 (国际规范命名)

                               CONSTRAINT pk_kyc_event_log PRIMARY KEY (id)
);

-- ==========================================
-- 表与字段注释
-- ==========================================
COMMENT ON TABLE kyc_event_log IS '统一业务事件日志表';
COMMENT ON COLUMN kyc_event_log.id IS '主键ID';
COMMENT ON COLUMN kyc_event_log.module IS '模块名称 (如: GROWTH_VALUE, GOODS_RATING)';
COMMENT ON COLUMN kyc_event_log.action IS '动作名称 (如: MANUAL_ADJUSTED, SCORE_UPDATED)';
COMMENT ON COLUMN kyc_event_log.entity_type IS '实体类型 (如: VEHICLE, DRIVER, GOODS)';
COMMENT ON COLUMN kyc_event_log.entity_id IS '实体对象ID';
COMMENT ON COLUMN kyc_event_log.entity_name IS '实体对象名称快照(冗余展示)';
COMMENT ON COLUMN kyc_event_log.old_data IS '变更前数据快照(JSON)';
COMMENT ON COLUMN kyc_event_log.new_data IS '变更后数据快照(JSON)';
COMMENT ON COLUMN kyc_event_log.summary IS '变更摘要/概览';
COMMENT ON COLUMN kyc_event_log.reason IS '操作原因/备注';
COMMENT ON COLUMN kyc_event_log.actor_id IS '执行者ID(操作人)';
COMMENT ON COLUMN kyc_event_log.actor_name IS '执行者姓名';
COMMENT ON COLUMN kyc_event_log.occurred_at IS '事件发生时间';

-- ==========================================
-- 极速查询复合索引 (依据大厂高频查询场景设计)
-- ==========================================
-- 1. 查某个实体对象的历史变更记录 (如: 查某辆车的调分历史)
CREATE INDEX idx_kyc_evt_log_1 ON kyc_event_log (entity_type, entity_id, occurred_at);

-- 2. 查某个操作人在某模块的操作 (如: 查管理员张三在成长值模块的操作)
CREATE INDEX idx_kyc_evt_log_2 ON kyc_event_log (actor_id, module, occurred_at);

-- 3. 全局审计大屏列表查询 (按模块和动作筛选，按时间倒序)
CREATE INDEX idx_kyc_evt_log_3 ON kyc_event_log (module, action, occurred_at);
