/**
 * Realm 框架的 AI 能力编排模块。
 *
 * <p><b>模块定位：</b>
 * 负责 AI 能力的上层编排，包括 Agent 生命周期管理、Prompt 版本控制、
 * RAG（检索增强生成）流水线以及 Tool（工具/函数）调用机制。
 * 本模块不实现底层的模型推理（那是模型提供商的职责），也不绑定特定的
 * AI 框架（如 Spring AI 或 LangChain4j），而是通过 SPI 适配器隔离。
 *
 * <p><b>架构红线（ArchUnit 强制检查）：</b>
 * <ul>
 *     <li><b>领域层不依赖 AI 框架</b>：{@code domain} 包禁止 import
 *         {@code org.springframework.ai.*} 或 {@code dev.langchain4j.*}。
 *         领域模型只关心 Prompt、Agent、RAG 的业务规则，具体的模型调用属于
 *         基础设施层（{@code infrastructure/provider}）。</li>
 *     <li><b>强制脱敏与安全（OWASP Top 10 for LLM）</b>：在将数据发送给外部
 *         大模型前，必须通过 {@code SensitiveDataMasker} 脱敏（防范 LLM06: Sensitive
 *         Information Disclosure），并对 Prompt 进行注入检测（防范 LLM01: Prompt Injection）。</li>
 *     <li><b>熔断与超时降级</b>：外部 LLM 调用必然伴随高延迟和不稳定性。所有模型调用
 *         必须强制接入 {@code realm-stability} 的 Resilience4j 熔断与超时控制，
 *         防止大模型卡死拖垮业务线程池。</li>
 *     <li><b>严禁保存完整对话历史在 JVM 内存</b>：多轮对话的上下文（Memory）必须
 *         持久化（数据库或 Redis），并设置严格的 TTL，防止 OOM。</li>
 * </ul>
 *
 * <p><b>Module Positioning:</b>
 * AI capability orchestration. Manages Agent lifecycles, Prompt templates,
 * RAG pipelines, and Tool calling. Does not implement model inference, nor does it
 * bind to a specific AI framework (adapters are used). Enforces strict data masking
 * and resilience for external LLM calls.
 *
 * @author QI Guang
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "realm-core",
                "realm-utils",
                "realm-audit-logging :: api",       // 记录 AI 生成的内容和调用日志（脱敏后）
                "realm-multitenancy :: api",        // 不同租户的 Agent 和知识库隔离
                "realm-stability :: api",           // LLM 调用的超时、重试、熔断
                "realm-messaging :: api",           // 异步流式响应的支撑
                "realm-workforce-identity :: api"   // 标识发起 AI 请求的用户（可选）
        }
)
package cn.realm.cloud.intelligence;
