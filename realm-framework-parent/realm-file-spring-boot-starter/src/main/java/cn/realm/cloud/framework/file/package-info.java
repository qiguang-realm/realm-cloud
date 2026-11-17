/**
 * Realm 框架的文件数据交换抽象层自动配置模块。
 *
 * <p><b>模块定位：</b>
 * 为应用提供文件导入导出的统一抽象。定义与具体引擎无关的注解模型与
 * {@code FileEngine} SPI，默认使用 FastExcel 提供流式读写，POI 作为可选的
 * 复杂格式引擎。内置基于文件魔数的真实类型检测与导入校验骨架，遵循 OWASP
 * 文件上传安全指南。
 *
 * <p><b>架构红线（ArchUnit 强制检查）：</b>
 * <ul>
 *     <li><b>核心零引擎依赖</b>：{@code core} 与 {@code annotation} 包禁止 import
 *         任何 FastExcel 或 POI 的类型。引擎实现必须放在 {@code engine} 子包下，
 *         并通过 {@code @ConditionalOnClass} 条件化。</li>
 *     <li><b>流式安全默认值</b>：写操作必须默认使用流式模式（{@code inMemory=false}），
 *         内存占用与行数无关。禁止在核心层提供“全量加载”的默认路径。</li>
 *     <li><b>不信任客户端元数据</b>：{@code FileTypeDetector} 必须基于文件内容
 *         （魔数）判断真实类型，绝不依赖客户端提供的 {@code Content-Type} 或扩展名。
 *         文件名必须经过 {@code FileNameSanitizer} 清理，防止路径遍历（CWE-22）。</li>
 *     <li><b>不承载文件存储</b>：本模块只处理 {@code InputStream} / {@code OutputStream}，
 *         文件的持久化由 {@code realm-object-storage} 负责，两者正交，不互相依赖。</li>
 * </ul>
 *
 * <p><b>Module Positioning:</b>
 * Provides a unified abstraction for file import/export. Defines engine-agnostic
 * annotations and a {@code FileEngine} SPI, defaulting to FastExcel for streaming I/O,
 * with POI as an optional engine for complex formats. Built-in magic-byte detection
 * and import validation follow the OWASP File Upload Cheat Sheet.
 *
 * @author QI Guang
 */
package cn.realm.cloud.framework.file;
