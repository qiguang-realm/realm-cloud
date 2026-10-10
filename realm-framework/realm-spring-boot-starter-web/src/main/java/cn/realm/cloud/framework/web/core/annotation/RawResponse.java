package cn.realm.cloud.framework.web.core.annotation;

import java.lang.annotation.*;

/**
 * 标记 Controller 方法返回原始响应，不经过 {@code GlobalResponseBodyAdvice} 自动包装。
 *
 * <p><b>使用场景：</b>
 * <ul>
 *     <li>第三方回调接口（如支付回调、微信推送），需要返回原始格式</li>
 *     <li>健康检查、Prometheus 指标等需要特定格式的接口</li>
 *     <li>需要精确控制响应体的接口</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @PostMapping("/callback/alipay")
 * @RawResponse
 * public String alipayCallback(@RequestBody String rawBody) {
 *     return "success";  // 直接返回，不包装为 ApiResponse
 * }
 * }</pre>
 *
 * @author QI Guang
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RawResponse {
}
