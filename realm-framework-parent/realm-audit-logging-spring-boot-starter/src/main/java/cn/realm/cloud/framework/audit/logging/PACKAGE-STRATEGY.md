📁 realm-audit-logging-spring-boot-starter 📁 详细包结构全景

realm-audit-logging-spring-boot-starter/
└── src/main/java/cn/realm/cloud/framework/audit/logging/
│
├── package-info.java                     # [必须] 模块定位与架构红线声明
│
├── autoconfigure/                        # Spring Boot 3 自动配置入口
│   ├── AuditLoggingAutoConfiguration.java       # [总控] 全局开关与核心 Bean 装配
│   ├── AuditAsyncAutoConfiguration.java         # [异步] 专用线程池与事件发布器
│   └── AuditWriterAutoConfiguration.java        # [写入] AuditEventRepository 的条件装配
│
├── core/                                 # 审计核心契约（零写入实现依赖）
│   ├── AuditEvent.java                   # 不可变审计事件（满足 NIST AU-3 最小要素集）
│   ├── AuditEventPublisher.java          # 发布接口（同步/异步策略由实现决定）
│   └── AuditLogProperties.java           # 全局开关、脱敏字段、异步队列容量
│
├── annotation/                           # 注解驱动
│   └── AuditLog.java                     # @AuditLog(action, resource, maskFields)
│
├── aspect/                               # AOP 捕获（入站适配器）
│   ├── AuditLogAspect.java               # 拦截 @AuditLog 方法，捕获主体/参数/结果/异常
│   └── AuditSpelResolver.java            # 解析注解中的 SpEL（如 targetId = "#userId"）
│
├── writer/                               # 审计写入 SPI 与默认实现
│   ├── AuditEventRepository.java         # [SPI] append-only 写入接口，禁止 update/delete
│   ├── Slf4jAuditEventRepository.java    # [默认] 独立 logger 写入（不依赖数据库）
│   ├── AsyncAuditEventPublisher.java     # [默认] 异步发布，CallerRunsPolicy 保底
│   └── JdbcAuditEventRepository.java     # [可选] 数据库写入（需应用显式启用）
│
├── integrity/                            # 防篡改（NIST AU-9 配套）
│   ├── HashChain.java                    # 前一条记录哈希 + 本条记录哈希的链式结构
│   └── AuditIntegrityVerifier.java       # 验证哈希链完整性（供审计查询侧使用）
│
├── masking/                              # 敏感字段脱敏
│   └── AuditFieldMasker.java             # 对参数/返回值中的 password/token/PII 做掩码
│
├── spi/                                  # 审计主体来源 SPI
│   └── AuditSubjectResolver.java         # 从 SubjectContext 获取操作人（不依赖 SecurityContextHolder）
│
└── metrics/                              # 可观测性
└── AuditMetrics.java                 # 审计写入耗时、失败计数、队列积压


realm-audit-logging-spring-boot-starter/
└── src/main/resources/META-INF/spring/
└── org.springframework.boot.autoconfigure.AutoConfiguration.imports
# [必须] Spring Boot 3 自动配置注册文件
