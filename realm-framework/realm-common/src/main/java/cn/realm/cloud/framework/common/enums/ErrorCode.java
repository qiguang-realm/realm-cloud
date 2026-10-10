package cn.realm.cloud.framework.common.enums;

import lombok.Getter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 错误码枚举，按业务领域分段管理。
 *
 * <p><b>分段规划：</b>
 * <table border="1">
 *     <caption>错误码分段</caption>
 *     <tr><th>区段</th><th>含义</th><th>示例</th></tr>
 *     <tr><td>0</td><td>成功</td><td>{@link #SUCCESS}</td></tr>
 *     <tr><td>1xxx</td><td>系统级</td><td>{@link #SYSTEM_ERROR}、{@link #PARAM_INVALID}</td></tr>
 *     <tr><td>2xxx</td><td>业务级</td><td>{@link #USER_NOT_FOUND}、{@link #ORDER_NOT_FOUND}</td></tr>
 *     <tr><td>3xxx</td><td>认证授权级</td><td>{@link #AUTH_UNAUTHORIZED}、{@link #AUTH_FORBIDDEN}</td></tr>
 *     <tr><td>4xxx</td><td>第三方/依赖级</td><td>{@link #REMOTE_SERVICE_ERROR}</td></tr>
 * </table>
 *
 * <p><b>业务细分建议：</b>每个领域预留 100 个错误码空间。
 * 例如 2xxx 分段下：
 * <ul>
 *     <li>2000-2099：用户领域</li>
 *     <li>2100-2199：订单领域</li>
 *     <li>2200-2299：账户领域</li>
 * </ul>
 *
 * <p><b>语义约定：</b>
 * <ul>
 *     <li>{@code code = 0}：业务成功（遵循 Unix 退出码惯例，0 表示成功）</li>
 *     <li>{@code code > 0}：业务失败，按区段分类</li>
 *     <li>所有错误码在枚举内<b>唯一</b>，静态块会校验</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 1. 按 code 查枚举（不存在的 code 返回 Optional.empty()）
 * ErrorCode code = ErrorCode.fromCode(2001).orElse(null);
 *
 * // 2. 按 code 查枚举（带默认兜底，推荐）
 * ErrorCode code2 = ErrorCode.fromCodeOrDefault(9999);  // → UNKNOWN_ERROR
 *
 * // 3. 判断响应是否成功
 * boolean success = ErrorCode.SUCCESS.isSuccess();
 * }</pre>
 *
 * @author QI Guang
 * @since 1.0.0
 */
@Getter
public enum ErrorCode {

    // ==================== 成功 ====================

    /**
     * 操作成功。
     *
     * <p>{@code code = 0} 遵循 Unix 退出码惯例（0 表示成功）。
     */
    SUCCESS(0, "操作成功"),

    // ==================== 系统级 (1xxx) ====================

    /**
     * 系统繁忙（未分类的系统异常兜底）。
     *
     * <p><b>触发场景：</b>未捕获的 {@link RuntimeException} 被全局异常处理器兜底。
     * <p><b>恢复建议：</b>稍后重试；若持续出现，联系技术支持排查日志。
     */
    SYSTEM_ERROR(1000, "系统繁忙，请稍后再试"),

    /**
     * 请求参数无效或格式错误。
     *
     * <p><b>触发场景：</b>Bean Validation 失败、JSON 反序列化失败。
     * <p><b>恢复建议：</b>检查请求参数格式。
     */
    PARAM_INVALID(1001, "请求参数无效或格式错误"),

    /**
     * 缺少必要的请求参数。
     */
    MISSING_PARAM(1002, "缺少必要的请求参数"),

    /**
     * 不支持的媒体类型（Content-Type 不匹配）。
     */
    MEDIA_TYPE_UNSUPPORTED(1003, "不支持的媒体类型"),

    /**
     * 不支持的 HTTP 方法。
     */
    METHOD_NOT_ALLOWED(1004, "不支持的 HTTP 方法"),

    // ==================== 业务级 (2xxx) ====================

    /**
     * 用户不存在。
     *
     * <p><b>触发场景：</b>按 ID / 用户名查询未命中。
     */
    USER_NOT_FOUND(2001, "用户不存在"),

    /**
     * 用户已被禁用。
     */
    USER_DISABLED(2002, "用户已被禁用"),

    /**
     * 订单不存在。
     */
    ORDER_NOT_FOUND(2101, "订单不存在"),

    /**
     * 订单状态不允许此操作。
     *
     * <p><b>触发场景：</b>对已取消的订单执行发货、对已完成的订单执行退款等。
     */
    ORDER_STATUS_INVALID(2102, "订单状态不允许此操作"),

    /**
     * 账户余额不足。
     */
    INSUFFICIENT_BALANCE(2201, "账户余额不足"),

    // ==================== 认证授权级 (3xxx) ====================

    /**
     * 未登录或 Token 已过期。
     *
     * <p><b>恢复建议：</b>重新登录获取新 Token。
     */
    AUTH_UNAUTHORIZED(3001, "未登录或 Token 已过期"),

    /**
     * 无权限访问该资源。
     *
     * <p><b>恢复建议：</b>检查用户角色 / 权限配置。
     */
    AUTH_FORBIDDEN(3002, "无权限访问该资源"),

    /**
     * Token 无效或已被篡改。
     */
    AUTH_TOKEN_INVALID(3003, "Token 无效或篡改"),

    // ==================== 第三方/依赖级 (4xxx) ====================

    /**
     * 依赖的第三方服务异常。
     */
    REMOTE_SERVICE_ERROR(4001, "依赖的第三方服务异常"),

    /**
     * 依赖服务调用超时。
     */
    REMOTE_TIMEOUT(4002, "依赖服务调用超时"),

    // ==================== 兜底 ====================

    /**
     * 未知错误（兜底）。
     *
     * <p>当 {@link #fromCode(int)} 未匹配任何已知错误码时返回此值。
     * <p>该错误码使用 <b>-1</b>，与业务错误码（正数）区分。
     */
    UNKNOWN_ERROR(-1, "未知错误");

    // ==================== 静态索引 ====================

    /**
     * {@code code → ErrorCode} 的 O(1) 查找映射（不可变）。
     *
     * <p>构建时校验 code 唯一性，冲突时抛 {@link IllegalStateException}。
     */
    private static final Map<Integer, ErrorCode> CODE_MAP;

    static {
        Map<Integer, ErrorCode> map = new HashMap<>();
        for (ErrorCode errorCode : values()) {
            ErrorCode existing = map.putIfAbsent(errorCode.code, errorCode);
            if (existing != null) {
                throw new IllegalStateException(
                        "错误码重复：" + errorCode.code + " 同时归属于 "
                                + existing.name() + " 和 " + errorCode.name());
            }
        }
        CODE_MAP = Collections.unmodifiableMap(map);
    }

    // ==================== 字段 ====================

    /**
     * 业务错误码。
     *
     * <p><b>约定：</b>
     * <ul>
     *     <li>0：成功</li>
     *     <li>正数：业务失败（按区段分类）</li>
     *     <li>-1：未知错误（兜底）</li>
     * </ul>
     */
    private final int code;

    /**
     * 错误消息（面向用户 / 开发者的提示）。
     */
    private final String message;

    // ==================== 构造器 ====================

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    // ==================== 静态工厂 ====================

    /**
     * 根据 code 查找 {@link ErrorCode}（返回 {@link Optional}）。
     *
     * <p>推荐的调用方式：
     * <pre>{@code
     * ErrorCode code = ErrorCode.fromCode(2001).orElse(ErrorCode.UNKNOWN_ERROR);
     * }</pre>
     *
     * @param code 业务错误码
     * @return 匹配的枚举；未匹配返回 {@link Optional#empty()}
     */
    public static Optional<ErrorCode> fromCode(int code) {
        return Optional.ofNullable(CODE_MAP.get(code));
    }

    /**
     * 根据 code 查找 {@link ErrorCode}，未匹配时返回 {@link #UNKNOWN_ERROR}。
     *
     * <p>适用于"无需区分 code 是否已知"的场景。
     *
     * @param code 业务错误码
     * @return 匹配的枚举；未匹配返回 {@link #UNKNOWN_ERROR}
     */
    public static ErrorCode fromCodeOrDefault(int code) {
        return CODE_MAP.getOrDefault(code, UNKNOWN_ERROR);
    }

    // ==================== 实例方法 ====================

    /**
     * 判断当前错误码是否表示成功。
     *
     * @return {@code true} 表示成功（{@code code == 0}）
     */
    public boolean isSuccess() {
        return this.code == 0;
    }

    /**
     * 判断当前错误码是否表示失败。
     *
     * @return {@code true} 表示失败（{@code code != 0}）
     */
    public boolean isFailure() {
        return !isSuccess();
    }

    // ==================== 调试支持 ====================

    /**
     * 获取所有 code 到 {@link ErrorCode} 的映射（仅供调试使用）。
     *
     * <p><b>注意：</b>返回的 Map 不可变，但<b>不推荐业务代码使用</b>——
     * 建议通过 {@link #fromCode(int)} 或 {@link #fromCodeOrDefault(int)} 查询。
     */
    public static Map<Integer, ErrorCode> getCodeMap() {
        return CODE_MAP;
    }
}
