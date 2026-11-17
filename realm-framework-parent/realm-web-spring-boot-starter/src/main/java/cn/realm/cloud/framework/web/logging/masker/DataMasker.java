package cn.realm.cloud.framework.web.logging.masker;

/**
 * 敏感数据脱敏器 SPI。
 *
 * <p><b>职责：</b>定义按“载体（Carrier）”进行敏感数据掩码的统一契约。
 * 下游可以扩展实现（如自定义 RPC 协议头脱敏），只需注册为 Spring Bean，
 * 会被自动收集到 {@link cn.realm.cloud.framework.web.logging.SensitiveDataMasker} 中。
 *
 * @author QI Guang
 */
public interface DataMasker {

    /**
     * 该脱敏器处理的载体类型。
     */
    Carrier carrier();

    /**
     * 执行脱敏。
     *
     * @param key   字段名或 Header 名（如 {@code password}, {@code Authorization}）
     * @param value 原始值
     * @return 脱敏后的值；如果不需要脱敏，返回原值
     */
    String mask(String key, String value);

    /**
     * 脱敏载体枚举。
     */
    enum Carrier {
        /**
         * 请求体 / 响应体
         */
        BODY,
        /**
         * URL 查询参数
         */
        QUERY,
        /**
         * HTTP 请求头 / 响应头
         */
        HEADER
    }
}
