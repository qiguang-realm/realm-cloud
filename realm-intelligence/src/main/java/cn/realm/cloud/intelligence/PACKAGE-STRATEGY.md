📁 realm-intelligence 📁 详细包结构全景

realm-intelligence/
└── src/main/java/cn/realm/cloud/intelligence/
│
├── package-info.java                     # [必须] 模块定位与架构红线声明
│
├── events/                               # 对外发布的领域事件（公共契约）
│   ├── package-info.java
│   ├── AgentExecutionCompletedEvent.java # 智能体执行完毕事件（供 analytics 消费统计 Token 和耗时）
│   └── AiContentGeneratedEvent.java      # AI 生成内容事件（供审计模块消费）
│
└── internal/                             # 私有实现区（Modulith 强制隔离）
│
├── agent/                            # === 子域 1：智能体管理 (Agent) ===
│   ├── domain/
│   │   ├── model/
│   │   │   ├── AiAgent.java          # [聚合根] 智能体实体（绑定特定的 Prompt、模型、工具）
│   │   │   └── AgentStatus.java      # 智能体状态
│   │   └── repository/
│   │       └── AgentRepository.java  # [端口]
│   ├── application/
│   │   ├── AgentCommandService.java  # 创建、修改、发布智能体
│   │   └── AgentExecutionService.java # 编排执行逻辑：组装上下文 -> 调用 LLM -> 调用工具 -> 返回
│   ├── infrastructure/
│   │   └── persistence/              # DO, Mapper, Adapter (intelligence_agent)
│   └── api/
│       ├── AgentAdminController.java # 管理端接口
│       ├── AgentChatController.java  # C端/内部调用的对话接口
│       └── dto/                      # ChatRequest, AgentCreateRequest
│
├── prompt/                           # === 子域 2：提示词管理 (Prompt) ===
│   ├── domain/
│   │   ├── model/
│   │   │   └── PromptTemplate.java   # [聚合根] 包含版本控制、变量占位符解析逻辑
│   │   └── repository/
│   │       └── PromptRepository.java
│   ├── application/
│   │   └── PromptApplicationService.java # 模板的渲染、历史版本回滚
│   ├── infrastructure/
│   │   └── persistence/              # 映射 intelligence_prompt
│   └── api/
│       └── PromptAdminController.java
│
├── rag/                              # === 子域 3：检索增强生成 (RAG) ===
│   ├── domain/
│   │   ├── model/
│   │   │   ├── KnowledgeBase.java    # [聚合根] 知识库元数据（绑定 Embedding 模型）
│   │   │   └── DocumentChunk.java    # 文档分片实体
│   │   └── repository/
│   │       └── KnowledgeBaseRepository.java
│   ├── application/
│   │   ├── RagIngestionService.java  # 文档解析、分片、向量化入库
│   │   └── RagRetrievalService.java  # 向量召回、重排、构建上下文
│   ├── infrastructure/
│   │   ├── parser/                   # 文档解析器（PDF, Markdown, Word）
│   │   └── vector/                   # 向量库适配器（如 PgVector, Milvus）
│   └── api/
│       └── KnowledgeBaseController.java
│
├── tool/                             # === 子域 4：工具调用 (Tool Calling / Function Calling) ===
│   ├── domain/
│   │   ├── model/
│   │   │   └── AiTool.java           # 工具定义（名称、描述、参数 JSON Schema）
│   │   └── repository/
│   │       └── ToolRegistry.java     # 工具注册中心
│   ├── application/
│   │   └── ToolExecutionService.java # 解析 LLM 的调用意图，执行本地方法或 API
│   └── api/
│       └── ToolAdminController.java
│
├── model/                            # === 子域 5：模型路由与适配 (Model Provider) ===
│   ├── domain/
│   │   ├── model/
│   │   │   └── ModelProviderConfig.java # [聚合根] 模型配置（API Key, Base URL, 温度参数等）
│   │   └── repository/
│   │       └── ModelConfigRepository.java
│   ├── application/
│   │   └── ModelRoutingService.java  # 根据业务策略（如成本、速度、可用性）动态路由到不同模型
│   └── infrastructure/
│       └── provider/                 # 具体大模型实现（Spring AI, LangChain4j 等封装）
│
└── support/                          # 支撑组件
├── TokenCounter.java             # Token 估算（用于成本控制和截断）
└── SensitiveDataMasker.java      # LLM 提示词中的敏感信息脱敏
