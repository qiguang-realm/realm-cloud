/**
 * Web 层全局处理器。
 *
 * <p><b>职责：</b>
 * <ul>
 *     <li>{@link cn.realm.cloud.framework.web.core.handler.GlobalExceptionHandler}：
 *         异常统一处理，转换为 {@code ApiResponse}</li>
 *     <li>{@link cn.realm.cloud.framework.web.core.handler.GlobalResponseBodyAdvice}：
 *         响应体自动包装，Controller 只返回业务数据</li>
 * </ul>
 *
 * <p><b>两者关系：</b>
 * <pre>
 * Controller 返回 UserVO
 *        ↓
 * GlobalResponseBodyAdvice 自动包装
 *        ↓
 * 返回 ApiResponse.success(UserVO)
 *
 * Controller 抛出 BusinessException
 *        ↓
 * GlobalExceptionHandler 捕获
 *        ↓
 * 返回 ApiResponse.error(ErrorCode)
 * </pre>
 *
 * <p><b>跳过包装的方式：</b>
 * <ul>
 *     <li>返回 {@code ApiResponse} / {@code ResponseEntity} / {@code String}</li>
 *     <li>返回 {@code Resource} / {@code byte[]}（文件下载）</li>
 *     <li>方法或类标注 {@code @RawResponse}</li>
 *     <li>URL 匹配排除列表（Actuator / Swagger 等）</li>
 * </ul>
 *
 * @author QI Guang
 * @since 1.0.0
 */
package cn.realm.cloud.framework.web.core.handler;
