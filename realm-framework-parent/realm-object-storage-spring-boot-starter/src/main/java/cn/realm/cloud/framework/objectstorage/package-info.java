/**
 * Realm 框架的对象存储统一抽象与 S3 协议实现自动配置模块。
 *
 * <p><b>模块定位：</b>
 * 定义与存储协议无关的 {@code ObjectStorageOperations} 契约，并基于 AWS SDK v2
 * 提供 S3 协议的默认实现。MinIO、阿里云 OSS 等厂商的专有 SDK 作为可选的方言适配器，
 * 通过条件装配按需引入。业务代码面向 {@code ObjectStorageTemplate} 编程，不绑定
 * 任何具体 SDK 的类型。
 *
 * <p><b>架构红线（ArchUnit 强制检查）：</b>
 * <ul>
 *     <li><b>核心零厂商依赖</b>：{@code core} 包禁止 import 任何 AWS SDK、MinIO SDK
 *         或阿里云 OSS SDK 的类型。方言实现必须放在 {@code dialect} 子包下，
 *         并通过 {@code @ConditionalOnClass} 条件化。</li>
 *     <li><b>S3 路径样式访问</b>：默认的 S3 适配器必须启用
 *         {@code pathStyleAccessEnabled(true)}。S3 兼容端点（MinIO、Ceph 等）
 *         通常不支持虚拟主机寻址，路径样式是兼容性的前提。</li>
 *     <li><b>凭证回退链</b>：当静态 access-key / secret-key 未配置时，S3 适配器
 *         必须回退到 AWS 的 {@code DefaultCredentialsProvider}，以支持 IAM 角色
 *         等无密钥运行方式。凭证提供者应可被应用通过
 *         {@code @ConditionalOnMissingBean} 覆盖。</li>
 *     <li><b>不承载业务文件语义</b>：模块只负责对象的存取与 URL 生成，
 *         不解析文件格式（Excel/CSV/PDF 由 {@code realm-file} 负责），
 *         不管理业务文件元数据（如归属用户、业务类型）。</li>
 * </ul>
 *
 * <p><b>Module Positioning:</b>
 * Defines a protocol-neutral {@code ObjectStorageOperations} contract and provides
 * a default S3 implementation via AWS SDK v2. Vendor-specific SDKs (MinIO, Aliyun OSS)
 * are wired as optional dialect adapters under {@code dialect/}, conditionally
 * assembled based on classpath presence.
 *
 * @author QI Guang
 */
package cn.realm.cloud.framework.objectstorage;
