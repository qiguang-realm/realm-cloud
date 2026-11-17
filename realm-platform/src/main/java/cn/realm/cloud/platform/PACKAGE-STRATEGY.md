📁 realm-platform 📁 详细包结构全景

realm-platform/
└── src/main/java/cn/realm/cloud/platform/
│
├── package-info.java                     # [必须] 模块定位与架构红线声明
│
└── internal/                             # 私有实现区（Modulith 强制隔离）
│
├── config/                           # 模块级配置
│   └── PlatformConfig.java           # 例如：任务调度的线程池配置（复用 scheduling 模块）
│
├── job/                              # === 子域 1：定时任务管理 (Job Management) ===
│   ├── domain/                       # 领域层（纯业务，无框架依赖）
│   │   ├── model/
│   │   │   ├── ScheduledJob.java     # [聚合根] 任务实体（包含启停状态机、Cron 校验逻辑）
│   │   │   ├── JobStatus.java        # 枚举：DRAFT, RUNNING, PAUSED, FAILED
│   │   │   └── JobLog.java           # 任务执行日志实体
│   │   └── repository/
│   │       ├── JobRepository.java    # [端口] 任务元数据持久化
│   │       └── JobLogRepository.java # [端口] 任务日志查询（通常只增不改）
│   ├── application/                  # 应用层（用例编排）
│   │   ├── JobCommandService.java    # 新增、修改、启停任务（校验合法性 -> 保存 -> 调用调度 SPI）
│   │   ├── JobQueryService.java      # 查询任务列表、执行日志
│   │   └── JobExecutionListener.java # 监听执行引擎的回调，更新本地任务日志状态
│   ├── infrastructure/               # 基础设施层
│   │   └── persistence/              # DO, Mapper, Adapter
│   │       ├── dataobject/           # JobDO, JobLogDO
│   │       ├── mapper/               # MyBatis-Plus Mapper
│   │       └── repository/           # 实现 domain 层的 Repository 接口
│   └── api/                          # 接口层
│       ├── JobAdminController.java   # REST API: /admin/platform/job
│       ├── dto/                      # JobCreateRequest, JobUpdateRequest, JobResponse
│       └── converter/
│           └── JobAdminConverter.java
│
└── server/                           # === 子域 2：服务器节点信息 (Server Node) ===
├── domain/
│   └── model/
│       └── ServerNode.java       # [聚合根] 服务器节点（IP, OS, CPU, 内存状态快照）
├── application/
│   └── ServerQueryService.java   # 查询节点状态（通常从 Actuator 或自定义采集器获取）
├── infrastructure/
│   └── client/
│       └── ActuatorClient.java   # 封装对 /actuator/info /actuator/health 的调用
└── api/
├── ServerAdminController.java# REST API: /admin/platform/server
└── dto/
└── ServerNodeResponse.java
